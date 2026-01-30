package com.nttdata.dataspace.ih.initialparticipant;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.identityhub.api.verifiablecredential.validation.ParticipantManifestValidator;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import java.util.List;
import java.util.Map;

import com.nttdata.dataspace.ih.manageparticipant.ParticipantServiceImpl;
import com.nttdata.dataspace.ih.manageparticipant.ParticipantConstants;


/**
 * Extension to seed initial participants into the system.
 */
public class InitialParticipantSeedExtension implements ServiceExtension {

    @Inject
    Monitor monitor;

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private ParticipantContextConfigService participantContextConfigService;

    private String[] participantIds;
    private String keyAlgo;
    private String keyCurve;
    private String credentialServiceBaseUrl;
    private String dspCallbackAddress;
    private String credentialsApiPath;
    private String protocolApiPath;

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
           
        }
    }

}
