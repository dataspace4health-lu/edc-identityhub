package com.nttdata.dataspace.ih.manageparticipant;

import com.nttdata.dataspace.ih.services.ParticipantService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.identityhub.api.verifiablecredential.validation.ParticipantManifestValidator;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceImplTest {

    @Mock
    private ParticipantContextService participantContextService;

    @Mock
    private Monitor monitor;

    @Mock
    private EdcHttpClient httpClient;

    @Mock
    private ParticipantManifestValidator validator;

    private ParticipantService participantService;
    private ParticipantManifest testManifest;
    private String participantId = "test-participant";
    private String notFound = "Not found";

    @BeforeEach
    void setUp() {
        participantService = new ParticipantServiceImpl();
        
        testManifest = ParticipantManifest.Builder.newInstance()
                .participantContextId(participantId)
                .did("did:web:" + participantId)
                .active(true)
                .key(KeyDescriptor.Builder.newInstance()
                        .keyGeneratorParams(Map.of("algorithm", "EdDSA", "curve", "Ed25519"))
                        .keyId(participantId + "-key")
                        .privateKeyAlias(participantId + "-alias")
                        .build())
                .roles(List.of())
                .build();
    }

    @Test
    void createParticipantShouldReturnNullWhenUsingHttpClient() {
        // Test the first overloaded method that returns null
        ServiceResult<CreateParticipantContextResponse> result = 
            participantService.createParticipant(testManifest, participantContextService, monitor, httpClient);

        assertThat(result).isNull();
    }

    @Test
    void createParticipantShouldCreateParticipantWhenValidManifest() {
        // Arrange
        CreateParticipantContextResponse expectedResponse = mock(CreateParticipantContextResponse.class);

        when(participantContextService.getParticipantContext(participantId))
                .thenReturn(ServiceResult.notFound(notFound));
        when(validator.validate(testManifest)).thenReturn(ValidationResult.success());
        when(participantContextService.createParticipantContext(testManifest))
                .thenReturn(ServiceResult.success(expectedResponse));

        // Act
        ServiceResult<CreateParticipantContextResponse> result = 
            participantService.createParticipant(testManifest, participantContextService, monitor, validator);

        // Assert
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isEqualTo(expectedResponse);
        verify(participantContextService).getParticipantContext(participantId);
        verify(validator).validate(testManifest);
        verify(participantContextService).createParticipantContext(testManifest);
        verify(monitor).info(anyString());
    }

    @Test
    void createParticipantShouldReturnConflictWhenParticipantAlreadyExists() {
        // Arrange
        when(participantContextService.getParticipantContext(participantId))
                .thenReturn(ServiceResult.success(null));

        // Act
        ServiceResult<CreateParticipantContextResponse> result = 
            participantService.createParticipant(testManifest, participantContextService, monitor, validator);

        // Assert
        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).contains("Participant already exist: " + participantId);
        verify(participantContextService).getParticipantContext(participantId);
        verify(participantContextService, never()).createParticipantContext(any());
        verify(monitor).info("Participant already exists with ID '" + participantId + "', will not re-create");
    }

    @Test
    void createParticipantShouldReturnConflictWhenValidationFails() {
        // Arrange
        when(participantContextService.getParticipantContext(participantId))
                .thenReturn(ServiceResult.notFound(notFound));
        
        ValidationResult validationResult = mock(ValidationResult.class);
        when(validationResult.succeeded()).thenReturn(false);
        when(validator.validate(testManifest)).thenReturn(validationResult);

        // Act
        ServiceResult<CreateParticipantContextResponse> result = 
            participantService.createParticipant(testManifest, participantContextService, monitor, validator);

        // Assert
        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages().get(0)).contains("Can not create participant with invalid details");
        verify(participantContextService).getParticipantContext(participantId);
        verify(validator).validate(testManifest);
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @Test
    void createParticipantShouldThrowExceptionWhenContextServiceFails() {
        // Arrange
        when(participantContextService.getParticipantContext(participantId))
                .thenReturn(ServiceResult.notFound(notFound));
        when(validator.validate(testManifest)).thenReturn(ValidationResult.success());
        when(participantContextService.createParticipantContext(testManifest))
                .thenReturn(ServiceResult.badRequest("Creation failed"));

        // Act & Assert
        assertThatThrownBy(() -> 
            participantService.createParticipant(testManifest, participantContextService, monitor, validator))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("Error creating participant " + participantId);

        verify(participantContextService).getParticipantContext(participantId);
        verify(validator).validate(testManifest);
        verify(participantContextService).createParticipantContext(testManifest);
    }
}
