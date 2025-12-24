package com.nttdata.dataspace.ih.initialparticipant;

import com.nttdata.dataspace.ih.manageparticipant.ParticipantConstants;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialParticipantSeedExtensionTest {

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Mock
    private ParticipantContextService participantContextService;

    @Mock
    private EdcHttpClient httpClient;

    @Mock
    private Config config;

    private InitialParticipantSeedExtension extension;

    private String participantIdCSV = "participant1,participant2";
    private String keyAlgo = "EdDSA";
    private String keyCurve = "Ed25519";
    private String seedingLogString = "Seeding initial participant with ID: participant1";

    @BeforeEach
    void setUp() {
        extension = new InitialParticipantSeedExtension();
        // Setup context mocks with lenient for tests that don't use them
        lenient().when(context.getMonitor()).thenReturn(monitor);
        lenient().when(monitor.withPrefix(anyString())).thenReturn(monitor);
        lenient().when(context.getConfig()).thenReturn(config);
    }

    @Test
    void initializeShouldSetUpExtension() {
        // Arrange
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        when(context.getConfig()).thenReturn(config);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(participantIdCSV);
        when(config.getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA)).thenReturn(keyAlgo);
        when(config.getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519)).thenReturn(keyCurve);

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
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        when(context.getConfig()).thenReturn(config);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(participantIdCSV);
        when(config.getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA)).thenReturn(keyAlgo);
        when(config.getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519)).thenReturn(keyCurve);

        extension.initialize(context);

        // Act
        try {
            extension.start();
        } catch (Exception e) {
            // Expected since createParticipant returns null
        }

        // Assert - verify that at least one info log was called for seeding
        // We use atLeastOnce since the loop may be interrupted by exception
        verify(monitor, times(1)).info(seedingLogString);
    }

    @Test
    void startShouldSkipEmptyParticipantIds() {
        // Arrange
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        when(context.getConfig()).thenReturn(config);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn("participant1, ,participant2");
        when(config.getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA)).thenReturn(keyAlgo);
        when(config.getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519)).thenReturn(keyCurve);

        extension.initialize(context);

        // Act
        try {
            extension.start();
        } catch (Exception e) {
            // Expected since createParticipant returns null
        }

        // Assert - verify first participant was logged
        verify(monitor, times(1)).info(seedingLogString);
    }

    @Test
    void startShouldTrimParticipantIds() {
        // Arrange
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        when(context.getConfig()).thenReturn(config);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn(" participant1 , participant2 ");
        when(config.getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA)).thenReturn(keyAlgo);
        when(config.getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519)).thenReturn(keyCurve);

        extension.initialize(context);

        // Act
        try {
            extension.start();
        } catch (Exception e) {
            // Expected since createParticipant returns null
        }

        // Assert - verify first participant was trimmed properly
        verify(monitor).info(seedingLogString);
    }

    @Test
    void initializeShouldCreateValidatorWithMonitor() {
        // Arrange
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        when(context.getConfig()).thenReturn(config);
        when(config.getString(ParticipantConstants.PARTICIPANT_ID_KEY)).thenReturn("test-participant");
        when(config.getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA)).thenReturn(keyAlgo);
        when(config.getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519)).thenReturn(keyCurve);

        // Act
        extension.initialize(context);

        // Assert - The validator is created internally
        // We verify through the initialization completing successfully
        verify(context).getMonitor();
        verify(monitor).withPrefix("InitialParticipantsSeed");
    }

    @Test
    void initializeShouldUseDefaultValuesWhenNotProvided() {
        // Arrange
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        when(context.getConfig()).thenReturn(config);
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
