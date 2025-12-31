package com.nttdata.dataspace.ih.initialparticipant;

import com.nttdata.dataspace.ih.manageparticipant.ParticipantConstants;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
        lenient().when(context.getService(ParticipantContextService.class)).thenReturn(participantContextService);
    }

    @Test
    void initializeShouldSetUpExtension() {
        // Act
        extension.initialize(context);

        // Assert
        verify(context).getMonitor();
        verify(monitor).withPrefix("InitialParticipantsSeed");
        verify(monitor).debug("Initial Participants Seed Extension initialized.");
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
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert
        verify(participantContextService, times(2)).createParticipantContext(any(ParticipantManifest.class));
    }

    @Test
    void startShouldSkipEmptyParticipantIds() {
        // Arrange
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn("participant1, ,participant2");
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));

        extension.initialize(context);

        // Act
        extension.start();

        // Assert - should create only 2 participants (skip empty string)
        verify(participantContextService, times(2)).createParticipantContext(any(ParticipantManifest.class));
    }

    @Test
    void startShouldTrimParticipantIds() {
        // Arrange
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(" participant1 , participant2 ");
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("Not found"));
        when(participantContextService.createParticipantContext(any(ParticipantManifest.class)))
                .thenReturn(ServiceResult.success(null));
        
        extension.initialize(context);

        // Act
        extension.start();

        // Assert - verify participants are trimmed correctly
        ArgumentCaptor<ParticipantManifest> participantCaptor = ArgumentCaptor.forClass(ParticipantManifest.class);
        verify(participantContextService, times(2)).createParticipantContext(participantCaptor.capture());
        
        var capturedManifests = participantCaptor.getAllValues();
        assertThat(capturedManifests).hasSize(2);
        assertThat(capturedManifests).extracting(ParticipantManifest::getParticipantId)
                .contains(PARTICIPANT1, PARTICIPANT2);
    }

    @Test
    void initializeShouldCreateValidatorWithMonitor() {
        // Act
        extension.initialize(context);

        // Assert - verify initialization completes successfully
        verify(context).getMonitor();
        verify(monitor).withPrefix("InitialParticipantsSeed");
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

}
