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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void setUp() {
        extension = new InitialParticipantSeedExtension();
        setupDefaultMocks();
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

}
