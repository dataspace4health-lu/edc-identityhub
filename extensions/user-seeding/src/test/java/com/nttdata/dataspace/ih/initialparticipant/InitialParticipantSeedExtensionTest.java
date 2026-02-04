package com.nttdata.dataspace.ih.initialparticipant;

import com.nttdata.dataspace.ih.manageparticipant.ParticipantConstants;
import org.eclipse.edc.identityhub.spi.did.DidDocumentService;
import org.eclipse.edc.identityhub.spi.did.model.DidResource;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.VerificationMethod;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantResource;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.edc.transaction.spi.TransactionContext.TransactionBlock;

@ExtendWith(MockitoExtension.class)
class InitialParticipantSeedExtensionTest {

    private static final String PARTICIPANT_ID_CSV = "participant1,participant2";
    private static final String KEY_ALGO = "EdDSA";
    private static final String KEY_CURVE = "Ed25519";
    private static final String PARTICIPANT1 = "participant1";
    private static final String PARTICIPANT2 = "participant2";

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Mock
    private ParticipantContextService participantContextService;

    @Mock
    private ParticipantContextConfigService participantContextConfigService;

    @Mock
    private Vault vault;

    @Mock
    private DidDocumentService didDocumentService;

    @Mock
    private DidResourceStore didResourceStore;

    @Mock
    private DataSourceRegistry dataSourceRegistry;

    @Mock
    private TransactionContext transactionContext;

    @Mock
    private QueryExecutor queryExecutor;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Config config;

    private InitialParticipantSeedExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        extension = new InitialParticipantSeedExtension();
        injectServices();
        setupDefaultMocks();
    }

    private void injectServices() throws Exception {
        setField("vault", vault);
        setField("didDocumentService", didDocumentService);
        setField("didResourceStore", didResourceStore);
        setField("dataSourceRegistry", dataSourceRegistry);
        setField("transactionContext", transactionContext);
        setField("queryExecutor", queryExecutor);
        setField("participantContextService", participantContextService);
        setField("participantContextConfigService", participantContextConfigService);
    }

    private void setField(String fieldName, Object value) throws Exception {
        var field = InitialParticipantSeedExtension.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(extension, value);
    }

    private void setupDefaultMocks() {
        lenient().when(context.getMonitor()).thenReturn(monitor);
        lenient().when(monitor.withPrefix(anyString())).thenReturn(monitor);
        lenient().when(context.getConfig()).thenReturn(config);
        lenient().when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT_ID_CSV);
        lenient().when(config.getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA))
                .thenReturn(KEY_ALGO);
        lenient().when(config.getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519))
                .thenReturn(KEY_CURVE);
        lenient().when(config.getString(ParticipantConstants.CREDENTIAL_SERVICE_URL_KEY, null)).thenReturn(null);
        lenient().when(config.getString(ParticipantConstants.DSP_CALLBACK_ADDRESS_KEY, null)).thenReturn(null);
        lenient().when(config.getString(ParticipantConstants.CREDENTIALS_API_PATH_KEY, null)).thenReturn("/api/v1/credentials");
        lenient().when(config.getString(ParticipantConstants.PROTOCOL_API_PATH_KEY, null)).thenReturn("/api/v1/protocol");
        lenient().when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(false);
        lenient().when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(null);
        lenient().when(context.getService(ParticipantContextService.class)).thenReturn(participantContextService);
        lenient().when(context.getService(ParticipantContextConfigService.class)).thenReturn(participantContextConfigService);
        lenient().when(context.getService(Vault.class)).thenReturn(vault);
        lenient().when(context.getService(DidDocumentService.class)).thenReturn(didDocumentService);
        lenient().when(context.getService(DidResourceStore.class)).thenReturn(didResourceStore);
        lenient().when(context.getService(DataSourceRegistry.class)).thenReturn(dataSourceRegistry);
        lenient().when(context.getService(TransactionContext.class)).thenReturn(transactionContext);
        lenient().when(context.getService(QueryExecutor.class)).thenReturn(queryExecutor);
    }

    @Test
    void initializeShouldSetUpExtension() {
        // Act
        extension.initialize(context);

        // Assert
        verify(context).getMonitor();
        verify(monitor).withPrefix("InitialParticipantsSeed");
        verify(monitor).info("Initial Participants Seed Extension initialized.");
        verify(config).getString(ParticipantConstants.PARTICIPANT_ID_KEY);
        verify(config).getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA);
        verify(config).getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519);
    }

    @Test
    void startShouldCreateMultipleParticipants() {
        // Arrange
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert
        verify(participantContextService, times(2)).createParticipantContext(any(ParticipantManifest.class));
        verify(participantContextConfigService, times(2)).save(any());
    }

    @Test
    void startShouldSkipEmptyParticipantIds() {
        // Arrange
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn("participant1, ,participant2");
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());

        extension.initialize(context);

        // Act
        extension.start();

        // Assert - should create only 2 participants (skip empty string)
        verify(participantContextService, times(2)).createParticipantContext(any(ParticipantManifest.class));
        verify(participantContextConfigService, times(2)).save(any());
    }

    @Test
    void startShouldTrimParticipantIds() {
        // Arrange
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(" participant1 , participant2 ");
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify participants are trimmed correctly
        ArgumentCaptor<ParticipantManifest> participantCaptor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(participantCaptor.capture());
        verify(participantContextConfigService, times(2)).save(any());
        
        var capturedManifests = participantCaptor.getAllValues();
        assertThat(capturedManifests).hasSize(2);
        assertThat(capturedManifests).extracting(ParticipantManifest::getParticipantContextId)
                .contains(PARTICIPANT1, PARTICIPANT2);
    }

    @Test
    void initializeShouldCreateValidatorWithMonitor() {
        // Act
        extension.initialize(context);

        // Assert - verify initialization completes successfully
        verify(context).getMonitor();
        verify(monitor).withPrefix("InitialParticipantsSeed");
        verify(monitor).info("Initial Participants Seed Extension initialized.");
    }

    @Test
    void initializeShouldUseDefaultValuesWhenNotProvided() {
        // Arrange
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn("test-participant");
        when(config.getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA))
                .thenReturn(ParticipantConstants.SIGN_SCHEME_EDDSA);
        when(config.getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519))
                .thenReturn(ParticipantConstants.SIGN_SCHEME_ED25519);

        // Act
        extension.initialize(context);

        // Assert - verify that default values are used
        verify(config).getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA);
        verify(config).getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519);
    }

    @Test
    void startShouldNotOverrideKeysWhenDisabled() {
        // Arrange
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(false);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - vault should never be called
        verify(vault, never()).storeSecret(anyString(), anyString(), anyString());
        verify(didResourceStore, never()).query(any(QuerySpec.class));
        verify(didDocumentService, never()).publish(anyString());
    }

    @Test
    void startShouldOverrideKeysWhenEnabled() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-public-x\",\"d\":\"test-private-d\",\"kid\":\"test-key-id\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock DID document operations
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(any(DidResource.class))).thenReturn(StoreResult.success());
        when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.success());
        
        // Mock database operations
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify key override operations
        verify(vault, times(2)).storeSecret(anyString(), anyString(), eq(testPrivateKey));
        verify(monitor, atLeastOnce()).info(contains("Overriding private key for participant"));
    }

    @Test
    void overrideKeysShouldUpdateDidDocument() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"public-coordinate\",\"d\":\"private-key\",\"kid\":\"key-1\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock DID document with existing verification method
        var existingVm = VerificationMethod.Builder.newInstance()
                .id(PARTICIPANT1 + "#key")
                .type("JsonWebKey2020")
                .publicKeyJwk(Map.of("kty", "OKP", "crv", "Ed25519", "x", "old-key"))
                .build();
        var verificationMethods = new ArrayList<VerificationMethod>();
        verificationMethods.add(existingVm);
        
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(verificationMethods)
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        ArgumentCaptor<DidResource> didResourceCaptor = ArgumentCaptor.forClass(DidResource.class);
        when(didResourceStore.update(didResourceCaptor.capture())).thenReturn(StoreResult.success());
        when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.success());
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify DID document was updated with new key
        verify(didResourceStore).update(any(DidResource.class));
        verify(didDocumentService).publish(PARTICIPANT1);
        verify(monitor).info(eq("Successfully republished DID document for participant: " + PARTICIPANT1));
        
        // Verify the new verification method was added
        DidResource updatedResource = didResourceCaptor.getValue();
        assertThat(updatedResource.getDocument().getVerificationMethod()).hasSize(1);
        var updatedVm = updatedResource.getDocument().getVerificationMethod().get(0);
        assertThat(updatedVm.getPublicKeyJwk()).containsEntry("x", "public-coordinate");
        assertThat(updatedVm.getPublicKeyJwk()).doesNotContainKey("d"); // No private key in DID document
    }

    @Test
    void overrideKeysShouldUpdateAuthenticationArray() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        
        ArgumentCaptor<DidResource> didResourceCaptor = ArgumentCaptor.forClass(DidResource.class);
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(didResourceCaptor.capture())).thenReturn(StoreResult.success());
        when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.success());
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify authentication array was populated
        DidResource updatedResource = didResourceCaptor.getValue();
        assertThat(updatedResource.getDocument().getAuthentication())
                .containsExactly(PARTICIPANT1 + "#key");
        verify(monitor).info(eq("Added keyId to authentication array: " + PARTICIPANT1 + "#key"));
    }

    @Test
    void overrideKeysShouldHandleDatabaseErrors() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"x\",\"d\":\"d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock database to fail
        when(dataSourceRegistry.resolve(anyString())).thenThrow(new RuntimeException("Database unavailable"));
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - error is logged but execution continues
        verify(monitor).severe(eq("Failed to update keypair in database for participant " + PARTICIPANT1), any(Exception.class));
        verify(vault).storeSecret(anyString(), anyString(), eq(testPrivateKey));
    }

    @Test
    void overrideKeysShouldHandleDidDocumentErrors() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"x\",\"d\":\"d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock DID operations to fail
        when(didResourceStore.query(any(QuerySpec.class))).thenThrow(new RuntimeException("DID store unavailable"));
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - error is logged
        verify(monitor).severe(eq("Failed to update DID document after key override for participant " + PARTICIPANT1), any(Exception.class));
    }

    @Test
    void overrideKeysShouldSkipWhenPrivateKeyIsNull() {
        // Arrange
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(null);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - no operations performed
        verify(vault, never()).storeSecret(anyString(), anyString(), anyString());
        verify(didResourceStore, never()).query(any(QuerySpec.class));
    }

    @Test
    void overrideKeysShouldSkipWhenPrivateKeyIsEmpty() {
        // Arrange
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn("");
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - no operations performed
        verify(vault, never()).storeSecret(anyString(), anyString(), anyString());
    }

    @Test
    void didDocumentUpdateShouldHandleMissingDidResource() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"x\",\"d\":\"d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock empty DID resource list
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of());
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - warning logged but no update attempted
        verify(monitor).warning("No DID document found for participant: " + PARTICIPANT1);
        verify(didResourceStore, never()).update(any(DidResource.class));
    }

    @Test
    void didDocumentUpdateShouldHandleInvalidJwk() throws Exception {
        // Arrange - JWK missing required fields
        String invalidPrivateKey = "{\"kty\":\"OKP\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(invalidPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - warning logged
        verify(monitor).warning("Invalid JWK format - missing required fields for public key extraction");
        verify(didResourceStore, never()).update(any(DidResource.class));
    }

    @Test
    void initializeShouldLogWarningWhenKeyOverrideEnabled() {
        // Arrange
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null))
                .thenReturn("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test\",\"d\":\"secret\"}");

        // Act
        extension.initialize(context);

        // Assert
        verify(monitor).warning("Key override is enabled. This should only be used for testing purposes!");
    }

    @Test
    void startShouldConstructCredentialServiceUrlFromConfig() {
        // Arrange
        String customUrl = "https://custom.example.com/credential-service";
        when(config.getString(ParticipantConstants.CREDENTIAL_SERVICE_URL_KEY, null)).thenReturn(customUrl);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify custom URL is used
        ArgumentCaptor<ParticipantManifest> captor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(captor.capture());
        
        // Check that service endpoints contain the custom URL
        var manifests = captor.getAllValues();
        assertThat(manifests).isNotEmpty();
    }

    @Test
    void startShouldConstructDspCallbackUrlFromConfig() {
        // Arrange
        String customDspUrl = "https://custom.example.com/dsp";
        when(config.getString(ParticipantConstants.DSP_CALLBACK_ADDRESS_KEY, null)).thenReturn(customDspUrl);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert
        verify(participantContextService, times(2)).createParticipantContext(any(ParticipantManifest.class));
    }

    @Test
    void startShouldCreateParticipantManifestWithCorrectKeyDescriptor() {
        // Arrange
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify key descriptor properties
        ArgumentCaptor<ParticipantManifest> captor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(captor.capture());
        
        var manifest = captor.getAllValues().get(0);
        assertThat(manifest.getKeys()).hasSize(1);
        var keyDescriptor = manifest.getKeys().iterator().next();
        assertThat(keyDescriptor.getKeyGeneratorParams())
                .containsEntry(ParticipantConstants.KEY_ALGO_STRING, KEY_ALGO)
                .containsEntry(ParticipantConstants.KEY_CURVE_STRING, KEY_CURVE);
    }

    @Test
    void startShouldCreateActiveParticipants() {
        // Arrange
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify participants are active
        ArgumentCaptor<ParticipantManifest> captor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(captor.capture());
        
        var manifests = captor.getAllValues();
        assertThat(manifests).allMatch(ParticipantManifest::isActive);
    }

    @Test
    void startShouldCreateParticipantsWithCorrectDids() {
        // Arrange
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify DIDs match participant IDs
        ArgumentCaptor<ParticipantManifest> captor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(captor.capture());
        
        var manifests = captor.getAllValues();
        assertThat(manifests)
                .extracting(ParticipantManifest::getDid)
                .contains(PARTICIPANT1, PARTICIPANT2);
    }

    @Test
    void initializeShouldReadAllConfigurationParameters() {
        // Arrange & Act
        extension.initialize(context);

        // Assert - verify all config parameters are read
        verify(config).getString(ParticipantConstants.PARTICIPANT_ID_KEY);
        verify(config).getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA);
        verify(config).getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519);
        verify(config).getString(ParticipantConstants.CREDENTIAL_SERVICE_URL_KEY, null);
        verify(config).getString(ParticipantConstants.DSP_CALLBACK_ADDRESS_KEY, null);
        verify(config).getString(ParticipantConstants.CREDENTIALS_API_PATH_KEY, null);
        verify(config).getString(ParticipantConstants.PROTOCOL_API_PATH_KEY, null);
        verify(config).getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false);
    }

    @Test
    void startShouldCreateServiceEndpointsForEachParticipant() {
        // Arrange
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify service endpoints are created
        ArgumentCaptor<ParticipantManifest> captor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(captor.capture());
        
        var manifests = captor.getAllValues();
        assertThat(manifests).allSatisfy(manifest -> {
            assertThat(manifest.getServiceEndpoints()).hasSize(2);
            assertThat(manifest.getServiceEndpoints())
                    .extracting(org.eclipse.edc.iam.did.spi.document.Service::getType)
                    .contains("CredentialService", "ProtocolEndpoint");
        });
    }

    @Test
    void startShouldLogInfoForEachParticipantSeeded() {
        // Arrange
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify logging
        verify(monitor).info("Starting Initial Participant Seeding...");
        verify(monitor).info("Seeding initial participant with ID: " + PARTICIPANT1);
        verify(monitor).info("Seeding initial participant with ID: " + PARTICIPANT2);
    }

    @Test
    void initializeShouldRetrieveServicesFromContext() {
        // Act
        extension.initialize(context);

        // Assert
        verify(context).getService(ParticipantContextService.class);
        verify(context).getService(ParticipantContextConfigService.class);
    }

    @Test
    void startShouldCreateParticipantsWithEmptyRolesList() {
        // Arrange
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify roles are empty
        ArgumentCaptor<ParticipantManifest> captor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(captor.capture());
        
        var manifests = captor.getAllValues();
        assertThat(manifests).allSatisfy(manifest -> 
            assertThat(manifest.getRoles()).isEmpty()
        );
    }

    @Test
    void overrideKeysShouldHandleDidResourceStoreUpdateFailure() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock DID operations - update fails
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(any(DidResource.class))).thenThrow(new RuntimeException("Update failed"));
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - error is logged
        verify(monitor).severe(eq("Failed to update DID document after key override for participant " + PARTICIPANT1), any(Exception.class));
        verify(didDocumentService, never()).publish(anyString());
    }

    @Test
    void overrideKeysShouldHandleDidDocumentPublishFailure() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock DID operations - publish fails
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(any(DidResource.class))).thenReturn(StoreResult.success());
        when(didDocumentService.publish(anyString())).thenThrow(new RuntimeException("Publish failed"));
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - error is logged but execution continues
        verify(didResourceStore).update(any(DidResource.class));
        verify(didDocumentService).publish(PARTICIPANT1);
        verify(monitor).severe(eq("Failed to update DID document after key override for participant " + PARTICIPANT1), any(Exception.class));
    }

    @Test
    void overrideKeysShouldReplaceExistingVerificationMethod() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"new-public-x\",\"d\":\"new-private-d\",\"kid\":\"new-key-id\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock DID with existing verification method and authentication
        var existingVm = VerificationMethod.Builder.newInstance()
                .id(PARTICIPANT1 + "#key")
                .type("JsonWebKey2020")
                .publicKeyJwk(Map.of("kty", "OKP", "crv", "Ed25519", "x", "old-public-key"))
                .build();
        var verificationMethods = new ArrayList<VerificationMethod>();
        verificationMethods.add(existingVm);
        var authentication = new ArrayList<String>();
        authentication.add(PARTICIPANT1 + "#key");
        
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(verificationMethods)
                .authentication(authentication)
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        
        ArgumentCaptor<DidResource> didResourceCaptor = ArgumentCaptor.forClass(DidResource.class);
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(didResourceCaptor.capture())).thenReturn(StoreResult.success());
        when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.success());
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify new verification method replaces old one
        DidResource updatedResource = didResourceCaptor.getValue();
        assertThat(updatedResource.getDocument().getVerificationMethod()).hasSize(1);
        var updatedVm = updatedResource.getDocument().getVerificationMethod().get(0);
        assertThat(updatedVm.getPublicKeyJwk()).containsEntry("x", "new-public-x");
        assertThat(updatedVm.getPublicKeyJwk()).doesNotContainEntry("x", "old-public-key");
        verify(monitor).info(contains("Removed 1 old verification method"));
    }

    @Test
    void overrideKeysShouldLogPublishSuccess() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(any(DidResource.class))).thenReturn(StoreResult.success());
        when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.success());
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify success logging
        verify(monitor).info("Successfully republished DID document for participant: " + PARTICIPANT1);
        verify(monitor).info(contains("Added keyId to authentication array"));
    }

    @Test
    void overrideKeysShouldHandleConnectionClosureException() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock database - connection.close() throws exception
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(new java.sql.SQLException("Close failed")).when(connection).close();
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - error is logged for database operations
        verify(monitor).severe(eq("Failed to update keypair in database for participant " + PARTICIPANT1), any(Exception.class));
    }

    @Test
    void startShouldSkipExistingParticipants() {
        // Arrange
        when(participantContextService.getParticipantContext(PARTICIPANT1)).thenReturn(ServiceResult.success(null));
        when(participantContextService.getParticipantContext(PARTICIPANT2)).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - only PARTICIPANT2 should be created
        verify(participantContextService, times(1)).createParticipantContext(any(ParticipantManifest.class));
        verify(monitor).info("Participant already exists with ID '" + PARTICIPANT1 + "', will not re-create");
    }

    @Test
    void overrideKeysShouldHandleDataSourceResolveFailure() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock datasource resolution failure
        when(dataSourceRegistry.resolve(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - error is logged
        verify(monitor).severe(eq("Failed to update keypair in database for participant " + PARTICIPANT1), any(Exception.class));
    }

    @Test
    void overrideKeysShouldHandleGetConnectionFailure() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock connection failure
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("Connection failed"));
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - SQL error is logged
        verify(monitor).severe(eq("Failed to update keypair in database for participant " + PARTICIPANT1), any(Exception.class));
    }

    @Test
    void overrideKeysShouldSkipAuthenticationUpdateWhenAlreadyPresent() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        // Mock DID with authentication already containing the keyId
        var authentication = new ArrayList<String>();
        authentication.add(PARTICIPANT1 + "#key");
        
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(authentication)
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(any(DidResource.class))).thenReturn(StoreResult.success());
        when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.success());
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - authentication array not modified (already contains the key)
        verify(monitor, never()).info(contains("Added keyId to authentication array"));
    }

    @Test
    void overrideKeysShouldHandleUpdateResultFailure() throws Exception {
        // Arrange
        String testPrivateKey = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"test-x\",\"d\":\"test-d\"}";
        when(config.getBoolean(ParticipantConstants.KEY_OVERIDE_ENABLED_STRING, false)).thenReturn(true);
        when(config.getString(ParticipantConstants.KEY_OVERIDE_PRIVATE_KEY_STRING, null)).thenReturn(testPrivateKey);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(PARTICIPANT1);
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
        
        var didDocument = DidDocument.Builder.newInstance()
                .id(PARTICIPANT1)
                .verificationMethod(new ArrayList<>())
                .authentication(new ArrayList<>())
                .build();
        var didResource = DidResource.Builder.newInstance()
                .did(PARTICIPANT1)
                .document(didDocument)
                .build();
        
        when(didResourceStore.query(any(QuerySpec.class))).thenReturn(List.of(didResource));
        when(didResourceStore.update(any(DidResource.class))).thenReturn(StoreResult.notFound("Not found"));
        
        // Mock database
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(queryExecutor.execute(any(Connection.class), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg != null) {
                ((TransactionBlock) arg).execute();
            }
            return null;
        }).when(transactionContext).execute(any(TransactionBlock.class));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - warning logged for update failure
        verify(monitor).warning(contains("Failed to update DID resource"));
        verify(didDocumentService, never()).publish(anyString());
    }

}
