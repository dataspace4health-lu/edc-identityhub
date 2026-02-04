package com.nttdata.dataspace.ih.initialparticipant;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.identityhub.api.verifiablecredential.validation.ParticipantManifestValidator;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.document.VerificationMethod;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.identityhub.spi.did.DidDocumentService;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nttdata.dataspace.ih.manageparticipant.ParticipantServiceImpl;

import okhttp3.MultipartBody.Part;

import com.nttdata.dataspace.ih.manageparticipant.ParticipantConstants;


/**
 * Extension to seed initial participants into the system.
 */
public class InitialParticipantSeedExtension implements ServiceExtension {

    @Setting(description = "The datasource to be used for keypair updates", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE, key = ParticipantConstants.KEYPAIR_DS_NAME)
    private String dataSourceName;

    @Inject
    Monitor monitor;

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private ParticipantContextConfigService participantContextConfigService;

    @Inject
    private Vault vault;

    @Inject
    private DidDocumentService didDocumentService;

    @Inject
    private DidResourceStore didResourceStore;

    @Inject
    private KeyParserRegistry keyParserRegistry;
    
    @Inject
    private DataSourceRegistry dataSourceRegistry;
    
    @Inject
    private TransactionContext transactionContext;
    
    @Inject
    private QueryExecutor queryExecutor;

    private String[] participantIds;
    private String keyAlgo;
    private String keyCurve;
    private String credentialServiceBaseUrl;
    private String dspCallbackAddress;
    private String credentialsApiPath;
    private String protocolApiPath;
    private boolean overrideKeyEnabled;
    private String privateKeyOverride;

    ParticipantManifestValidator validator;
    

    @Override
    public void initialize(ServiceExtensionContext context) {
        // Initialization logic here   
        monitor = context.getMonitor().withPrefix("InitialParticipantsSeed");
        monitor.info("Initial Participants Seed Extension initialized.");

        validator = new ParticipantManifestValidator(monitor);
        
        // Retrieve the ParticipantContextService from the context
        participantContextService = context.getService(ParticipantContextService.class);
        
        // Retrieve the ParticipantContextConfigService from the context
        participantContextConfigService = context.getService(ParticipantContextConfigService.class);

        //if no participantIds provided, system will throw an exception
        participantIds = context.getConfig().getString(ParticipantConstants.PARTICIPANT_ID_KEY).split(",");
        keyAlgo = context.getConfig().getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA);
        keyCurve = context.getConfig().getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519);
        
        // Read service endpoint URLs from config
        credentialServiceBaseUrl = context.getConfig().getString(ParticipantConstants.CREDENTIAL_SERVICE_URL_KEY, null);
        dspCallbackAddress = context.getConfig().getString(ParticipantConstants.DSP_CALLBACK_ADDRESS_KEY, null);
        
        // Read API paths from config
        credentialsApiPath = context.getConfig().getString(ParticipantConstants.CREDENTIALS_API_PATH_KEY, null);
        protocolApiPath = context.getConfig().getString(ParticipantConstants.PROTOCOL_API_PATH_KEY, null);
        
        // Read key override configuration for testing
        overrideKeyEnabled = context.getConfig().getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false);
        if (overrideKeyEnabled) {
            monitor.warning("Key override is enabled. This should only be used for testing purposes!");
            privateKeyOverride = context.getConfig().getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null);
        }
    }

    @Override
    public void start() {
        monitor.info("Starting Initial Participant Seeding...");
        
        ParticipantServiceImpl participantServiceImpl = new ParticipantServiceImpl();

         //The system will work as expected for 1 participantId but in case of multiple ids, we will have to add logic to handle the service end points properly
        for (String participantId : participantIds) {
            
            participantId = participantId.trim();
            if (participantId.isEmpty()) {
                continue; 
            }

            monitor.info("Seeding initial participant with ID: " + participantId);

            // Construct credential service endpoint URL
            // The URL should include the participant context ID in the path: /v1/participants/{participantId}
            // The EDC client will append /presentations/query to this base URL
            String credentialServiceUrl;
            if (credentialServiceBaseUrl != null && !credentialServiceBaseUrl.isEmpty()) {
                // If configured, use it but ensure participant ID is included
                credentialServiceUrl = credentialServiceBaseUrl;
            } else {
                // Fallback: derive from participant DID using configured API path
                // Base64-URL encode the participant ID for use in the path
                String participantContextIdEncoded = java.util.Base64.getUrlEncoder().encodeToString(participantId.getBytes());
                credentialServiceUrl = "https://" + participantId.replace("did:web:", "") + credentialsApiPath + ParticipantConstants.PARTICIPANT_CREDENTIAL_EP + participantContextIdEncoded;
            }
            
            // Construct DSP protocol endpoint URL
            String protocolEndpointUrl;
            if (dspCallbackAddress != null && !dspCallbackAddress.isEmpty()) {
                protocolEndpointUrl = dspCallbackAddress;
            } else {
                // Fallback: derive from participant DID using configured API path
                protocolEndpointUrl = "https://" + participantId.replace("did:web:", "") + protocolApiPath;
            }
            
            // Create service endpoints for the DID document
            var credentialServiceEndpoint = new Service(
                    participantId + "#CredentialService",
                    "CredentialService",
                    credentialServiceUrl
            );
            
            var protocolServiceEndpoint = new Service(
                    participantId + "#ProtocolEndpoint",
                    "ProtocolEndpoint",
                    protocolEndpointUrl
            );

            ParticipantManifest participantManifest = ParticipantManifest.Builder.newInstance()
                        .participantContextId(participantId)
                        .did(participantId)
                        .active(true)
                        .serviceEndpoint(credentialServiceEndpoint)
                        .serviceEndpoint(protocolServiceEndpoint)
                        .key(KeyDescriptor.Builder.newInstance()
                                .keyGeneratorParams(Map.of(ParticipantConstants.KEY_ALGO_STRING, keyAlgo, ParticipantConstants.KEY_CURVE_STRING, keyCurve))
                                .keyId(ParticipantConstants.PARTICIPANT_PUBLIC_KEY_ALIAS_FORMAT.formatted(participantId))
                                .privateKeyAlias(ParticipantConstants.PARTICIPANT_PRIVATE_KEY_ALIAS.formatted(participantId))
                                .build())
                        .roles(List.of())
                        .build();

            participantServiceImpl.createParticipant(participantManifest, participantContextService, monitor, validator, participantContextConfigService);
            
            // Override keys if enabled (testing only)
            overrideParticipantKeys(participantId);
        }
    }

    private void overrideParticipantKeys(String participantId) {
        if (!overrideKeyEnabled) {
            return;
        }
        
        String privateKeyAlias = ParticipantConstants.PARTICIPANT_PRIVATE_KEY_ALIAS.formatted(participantId);  
        
        if (privateKeyOverride != null && !privateKeyOverride.isEmpty()) {
            monitor.info("Overriding private key for participant: " + participantId);
            vault.storeSecret(participantId, privateKeyAlias, privateKeyOverride);
            
            // Update the database keypair_resource table
            try {
                updateKeyPairInDatabase(participantId);
            } catch (Exception e) {
                monitor.severe("Failed to update keypair in database for participant " + participantId, e);
            }
            
            // Republish the DID document with the updated public key
            try {
                updateDidDocumentWithNewKey(participantId);
            } catch (Exception e) {
                monitor.severe("Failed to update DID document after key override for participant " + participantId, e);
            }
        }
    }
    
    /**
     * Update the DID document with the new public key derived from the overridden private key.
     * this method retrieves the DID document, updates the verification method with the new public key,
     * @param participantId
     * @throws Exception
     */
    private void updateDidDocumentWithNewKey(String participantId) throws Exception {
        // Parse the new private key from JWK
        var objectMapper = new ObjectMapper();
        var privateKeyJwk = objectMapper.readValue(privateKeyOverride, Map.class);
        
        // Extract the public key coordinate from the JWK
        String publicX = (String) privateKeyJwk.get("x");
        String kty = (String) privateKeyJwk.get("kty");
        String crv = (String) privateKeyJwk.get("crv");
        String kid = (String) privateKeyJwk.get("kid");
        
        monitor.info("Extracted public key components - kty: " + kty + ", crv: " + crv + ", x: " + publicX);
        
        if (publicX == null || kty == null || crv == null) {
            monitor.warning("Invalid JWK format - missing required fields for public key extraction");
            return;
        }
        
        // Build the public key JWK
        Map<String, Object> publicKeyJwk = Map.of(
            "kty", kty,
            "crv", crv,
            "x", publicX,
            "kid", kid != null ? kid : participantId + "#key"
        );
        
        monitor.info("Built public key JWK: " + objectMapper.writeValueAsString(publicKeyJwk));
        
        // Find the DID document for this participant
        var didResourceList = didResourceStore.query(
            org.eclipse.edc.participantcontext.spi.types.ParticipantResource.queryByParticipantContextId(participantId).build()
        );
        
        if (didResourceList.isEmpty()) {
            monitor.warning("No DID document found for participant: " + participantId);
            return;
        }
        
        var didResource = didResourceList.iterator().next();
        var didDocument = didResource.getDocument();
        var keyId = participantId + "#key";
        
        monitor.info("Found DID document: " + didDocument.getId() + " with " + didDocument.getVerificationMethod().size() + " verification methods");
        
        // Update the verification method with the new public key
        var verificationMethods = didDocument.getVerificationMethod();
        var oldMethodCount = verificationMethods.size();
        verificationMethods.removeIf(vm -> vm.getId().equals(keyId));
        monitor.info("Removed " + (oldMethodCount - verificationMethods.size()) + " old verification method(s) with keyId: " + keyId);
        
        verificationMethods.add(VerificationMethod.Builder.newInstance()
                .id(keyId)
                .publicKeyJwk(publicKeyJwk)
                .controller(didDocument.getId())
                .type("JsonWebKey2020")
                .build());
        
        // Add the key to authentication array for token verification
        var authentication = didDocument.getAuthentication();
        if (!authentication.contains(keyId)) {
            authentication.clear();  // Clear old references
            authentication.add(keyId);
            monitor.info("Added keyId to authentication array: " + keyId);
        }
        
        // Update the DID resource in the store
        var updateResult = didResourceStore.update(didResource);
        if (updateResult.failed()) {
            monitor.warning("Failed to update DID resource: " + updateResult.getFailureDetail());
            return;
        }
        
        // Publish the updated DID document
        var publishResult = didDocumentService.publish(didResource.getDid());
        if (publishResult.succeeded()) {
            monitor.info("Successfully republished DID document for participant: " + participantId);
        } else {
            monitor.warning("Failed to publish updated DID document: " + publishResult.getFailureDetail());
        }
    }

    /**
     * Update the keypair_resource table in the database with the new public key.
     * this is needed because the EDC key management stores both public and private keys in this table
     * this method ensures that the public key is updated to match the overridden private key.
     * @param participantId
     */
    private void updateKeyPairInDatabase(String participantId) {
        monitor.info("Updating keypair_resource table for participant: " + participantId);
        
        // Extract public key only (remove private 'd' parameter)
        String publicKeyOnlyJson;
        try {
            var objectMapper = new ObjectMapper();
            var jwk = objectMapper.readValue(privateKeyOverride, Map.class);
            var publicJwk = new HashMap<>(jwk);
            publicJwk.remove("d"); // Remove private key parameter
            publicKeyOnlyJson = objectMapper.writeValueAsString(publicJwk);
            monitor.info("Extracted public key JWK (without private 'd' parameter) for database storage");
        } catch (Exception e) {
            monitor.severe("Failed to extract public key from JWK", e);
            throw new RuntimeException(e);
        }
        
        transactionContext.execute(() -> {
            try {
                var dataSource = dataSourceRegistry.resolve(dataSourceName);
                try (var connection = dataSource.getConnection()) {
                    var sql = "UPDATE keypair_resource SET serialized_public_key = ? WHERE participant_context_id = ?";
                    var updated = queryExecutor.execute(connection, sql, publicKeyOnlyJson, participantId);
                    monitor.info("Updated " + updated + " keypair record(s) in database for participant: " + participantId);
                } catch (SQLException e) {
                    monitor.severe("SQL error updating keypair_resource table", e);
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                monitor.severe("Failed to update keypair_resource table", e);
                throw new RuntimeException(e);
            }
            return null;
        });
    }

}
