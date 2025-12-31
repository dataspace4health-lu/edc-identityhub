/*
 *  Copyright (c) 2024 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package com.nttdata.dataspace.ih.superuser;

import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuperuserSeedExtensionTest {

    private static final String TEST_SUPERUSER = "test-superuser";
    private static final String NOT_FOUND = "Not found";
    private static final String SECRET_VALUE = "secret-value";
    private static final String CUSTOM_ADMIN_ID = "custom-admin";
    private static final String APIKEY_SUFFIX = "-apikey";

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Mock
    private ParticipantContextService participantContextService;

    @Mock
    private Vault vault;

    @InjectMocks
    private SuperuserSeedExtension extension;

    @BeforeEach
    void setUp() {
        // Configure monitor mock with lenient to avoid UnnecessaryStubbingException
        lenient().when(context.getMonitor()).thenReturn(monitor);
        lenient().when(monitor.withPrefix(anyString())).thenReturn(monitor);
        
        // Default settings with lenient stubs
        lenient().when(context.getSetting(
                eq(SuperuserSeedExtension.SUPERUSER_PARTICIPANT_ID_PROPERTY), 
                anyString()))
                .thenReturn(TEST_SUPERUSER);
        lenient().when(context.getSetting(
                eq(SuperuserSeedExtension.SUPERUSER_DID_PROPERTY), 
                anyString()))
                .thenReturn("did:web:" + TEST_SUPERUSER);
        lenient().when(context.getSetting(
                eq(SuperuserSeedExtension.MAX_RETRIES_PROPERTY), 
                anyString()))
                .thenReturn("5");
        lenient().when(context.getSetting(
                eq(SuperuserSeedExtension.RETRY_DELAY_MS_PROPERTY), 
                anyString()))
                .thenReturn("2000");
    }

    @Test
    void testName() {
        assertThat(extension.name()).isEqualTo("Superuser Seed Extension");
    }

    @Test
    void testInitializeWithDefaultSettings() {
        extension.initialize(context);

        verify(context).getSetting(
                eq(SuperuserSeedExtension.SUPERUSER_PARTICIPANT_ID_PROPERTY),
                eq(SuperuserSeedExtension.DEFAULT_SUPER_USER_PARTICIPANT_ID));
        verify(context).getSetting(
                eq(SuperuserSeedExtension.SUPERUSER_DID_PROPERTY),
                anyString());
        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void testStartWhenSuperUserDoesNotExistShouldCreateAndVerify() {
        // Given
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(TEST_SUPERUSER);
        
        // Superuser does not exist
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(participantContext));
        
        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(createMockCreateParticipantContextResponse()));
        
        // Vault secrets exist
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        // When
        extension.start();

        // Then
        verify(participantContextService).createParticipantContext(any());
        verify(vault, times(3)).resolveSecret(anyString());
        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void testStartWhenSuperUserAlreadyExistsShouldVerifyOnly() {
        // Given
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(TEST_SUPERUSER);
        
        // Superuser already exists
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.success(participantContext));
        
        // Vault secrets exist
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        // When
        extension.start();

        // Then
        verify(participantContextService, never()).createParticipantContext(any());
        verify(vault, times(3)).resolveSecret(anyString());
        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void testStartWhenVaultSecretsMissingShouldRetryAndSucceed() {
        // Given
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(TEST_SUPERUSER);
        
        // Superuser does not exist initially
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(participantContext))
                .thenReturn(ServiceResult.success(participantContext));
        
        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(createMockCreateParticipantContextResponse()));
        
        // Vault secrets missing on first attempt, present on second
        when(vault.resolveSecret(anyString()))
                .thenReturn(null)
                .thenReturn(SECRET_VALUE);

        // When
        extension.start();

        // Then
        verify(monitor, atLeastOnce()).warning(anyString());
        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void testStartWhenCreationFailsShouldRetry() {
        // Given
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(TEST_SUPERUSER);
        
        // Superuser does not exist
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(participantContext));
        
        // Creation fails first, succeeds second
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.badRequest("Creation failed"))
                .thenReturn(ServiceResult.success(createMockCreateParticipantContextResponse()));
        
        // Vault secrets exist
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        // When
        extension.start();

        // Then
        verify(participantContextService, times(2)).createParticipantContext(any());
        verify(monitor, atLeastOnce()).warning(anyString());
    }

    @Test
    void testStartWhenMaxRetriesExceededShouldThrowException() {
        // Given
        extension.initialize(context);
        
        // Superuser does not exist and creation always fails
        // Return notFound consistently for all retry attempts
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.notFound(NOT_FOUND));
        
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.badRequest("Creation failed"));
        
        // No vault secrets
        when(vault.resolveSecret(anyString())).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> extension.start())
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("Failed to bootstrap super-user after 5 attempts");
        
        verify(monitor, atLeastOnce()).severe(anyString());
    }

    @Test
    void testStartWhenVaultSecretsNeverAppearShouldThrowException() {
        // Given
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(TEST_SUPERUSER);
        
        // Superuser does not exist initially, but created successfully
        // After creation, getParticipantContext succeeds but vault secrets never appear
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))  // First check - doesn't exist
                .thenReturn(ServiceResult.success(participantContext)); // After creation - exists
        
        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(createMockCreateParticipantContextResponse()));
        
        // Vault secrets never appear (always null)
        when(vault.resolveSecret(anyString())).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> extension.start())
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("Failed to bootstrap super-user after 5 attempts");
    }

    @Test
    void testStartWhenSuperUserExistsButVaultCorruptedShouldRetryAndRecover() {
        // Given
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(TEST_SUPERUSER);
        
        // Superuser exists
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.success(participantContext));
        
        // Vault secrets missing initially, then appear
        when(vault.resolveSecret(anyString()))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(SECRET_VALUE);

        // When
        extension.start();

        // Then
        verify(monitor, atLeastOnce()).warning(anyString());
        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void testStartVerifyAllVaultSecretsChecked() {
        // Given
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(TEST_SUPERUSER);
        
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.success(participantContext));
        
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        // When
        extension.start();

        // Then - Verify all 3 secrets are checked (API key, private key, STS secret)
        verify(vault).resolveSecret(TEST_SUPERUSER + APIKEY_SUFFIX);
        verify(vault).resolveSecret(TEST_SUPERUSER + "-alias");
        verify(vault).resolveSecret(TEST_SUPERUSER + "-sts-client-secret");
    }

    @Test
    void testStartwithCustomParticipantId() {
        // Given
        when(context.getSetting(
                eq(SuperuserSeedExtension.SUPERUSER_PARTICIPANT_ID_PROPERTY), 
                anyString()))
                .thenReturn(CUSTOM_ADMIN_ID);
        
        extension.initialize(context);
        
        var participantContext = createMockParticipantContext(CUSTOM_ADMIN_ID);
        
        when(participantContextService.getParticipantContext(CUSTOM_ADMIN_ID))
                .thenReturn(ServiceResult.success(participantContext));
        
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        // When
        extension.start();

        // Then - getParticipantContext is called twice: once in tryBootstrap() and once in retrieveParticipantContext()
        verify(participantContextService, times(2)).getParticipantContext(CUSTOM_ADMIN_ID);
        verify(vault).resolveSecret(CUSTOM_ADMIN_ID + APIKEY_SUFFIX);
        verify(vault).resolveSecret(CUSTOM_ADMIN_ID + "-alias");
        verify(vault).resolveSecret(CUSTOM_ADMIN_ID + "-sts-client-secret");
    }

    @Test
    void testStartWithInterruptedExceptionShouldHandleGracefully() {
        // Given
        extension.initialize(context);
        
        // Superuser does not exist
        when(participantContextService.getParticipantContext(TEST_SUPERUSER))
                .thenReturn(ServiceResult.notFound(NOT_FOUND));
        
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.badRequest("Failed"));

        // Interrupt the thread during retry
        Thread.currentThread().interrupt();

        // When
        extension.start();

        // Then - should exit without throwing
        verify(monitor, atLeastOnce()).warning(anyString());
        assertThat(Thread.interrupted()).isTrue(); // Clear interrupt flag
    }

    // Helper methods

    private ParticipantContext createMockParticipantContext(String participantId) {
        var mockContext = mock(ParticipantContext.class);
        when(mockContext.getParticipantContextId()).thenReturn(participantId);
        when(mockContext.getApiTokenAlias()).thenReturn(participantId + APIKEY_SUFFIX);
        return mockContext;
    }

    private CreateParticipantContextResponse createMockCreateParticipantContextResponse() {
        return new CreateParticipantContextResponse(
                "test-api-key-with-proper-length",
                "test-client-id",
                "test-client-secret"
        );
    }

}
