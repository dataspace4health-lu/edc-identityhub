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

package org.ntt.com.ih.superuser;

import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantContext;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantContext.Builder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class ParticipantContextSeedExtensionTest {

    private static final String SUPER_USER = "super-user";
    private static final String SUPER_USER_DID = "did:web:super-user";
    private static final String GENERATED_API_KEY = "c3VwZXItdXNlcg==.generated-random-key";
    private static final String API_KEY_OVERRIDE = "c3VwZXItdXNlcg==.override-api-key";
    private static final String INVALID_API_KEY = "invalid-key-without-dot";
        private static final String NOT_FOUND = "not found";
        private static final String SECRET_VALUE = "secret-value";
        private static final String ERROR_BOOTSTRAP = "Failed to bootstrap super-user";
        private static final String SUPERUSER_APIKEY_ALIAS = "super-user-apikey";
        private static final String SUPERUSER_ALIAS = "super-user-alias";
        private static final String SUPERUSER_STS_CLIENT_SECRET = "super-user-sts-client-secret";
    
    private final ParticipantContextService participantContextService = mock();
    private final Vault vault = mock();
    private final Monitor monitor = mock();

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        context.registerService(ParticipantContextService.class, participantContextService);
        context.registerService(Vault.class, vault);
        context.registerService(Monitor.class, monitor);
    }

    @Test
    void initializeShouldSetDefaultConfiguration(ParticipantContextSeedExtension ext,
                                                  ServiceExtensionContext context) {
        ext.initialize(context);

        verify(monitor, atLeastOnce()).withPrefix(contains("SuperUserSeed"));
        verify(context, atLeastOnce()).getSetting(
                eq(ParticipantContextSeedExtension.SUPERUSER_PARTICIPANT_ID_PROPERTY),
                eq(ParticipantContextSeedExtension.DEFAULT_SUPER_USER_PARTICIPANT_ID)
        );
    }

    @Test
    void initializeShouldUseCustomConfiguration(ParticipantContextSeedExtension ext,
                                                 ServiceExtensionContext context) {
        var customParticipantId = "custom-admin";
        var customDid = "did:web:custom-admin";
        
        when(context.getSetting(eq(ParticipantContextSeedExtension.SUPERUSER_PARTICIPANT_ID_PROPERTY), anyString()))
                .thenReturn(customParticipantId);
        when(context.getSetting(eq(ParticipantContextSeedExtension.SUPERUSER_DID_PROPERTY), anyString()))
                .thenReturn(customDid);

        ext.initialize(context);

        verify(context).getSetting(
                eq(ParticipantContextSeedExtension.SUPERUSER_PARTICIPANT_ID_PROPERTY),
                anyString()
        );
        verify(context).getSetting(
                eq(ParticipantContextSeedExtension.SUPERUSER_DID_PROPERTY),
                anyString()
        );
    }

    @Test
    void startShouldCreateSuperUserWhenNotExists(ParticipantContextSeedExtension ext,
                                                  ServiceExtensionContext context) {
        // Super user doesn't exist
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(
                        new CreateParticipantContextResponse(GENERATED_API_KEY, null, null)));

        // Vault secrets available
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        ext.initialize(context);
        ext.start();

        verify(participantContextService, times(2)).getParticipantContext(eq(SUPER_USER));
        verify(participantContextService).createParticipantContext(any());
        verify(vault, times(3)).resolveSecret(anyString());
    }

    @Test
    void startShouldSkipCreationWhenSuperUserExists(ParticipantContextSeedExtension ext,
                                                     ServiceExtensionContext context) {
        // Super user already exists
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Vault secrets available
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        ext.initialize(context);
        ext.start();

        verify(participantContextService).getParticipantContext(eq(SUPER_USER));
        verify(participantContextService, never()).createParticipantContext(any());
        verify(vault, times(3)).resolveSecret(anyString());
    }

    @Test
    void startShouldFailWhenCreationFails(ParticipantContextSeedExtension ext,
                                          ServiceExtensionContext context) {
        // Super user doesn't exist
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.notFound(NOT_FOUND));

        // Creation fails
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.badRequest("creation failed"));

        ext.initialize(context);

        assertThatThrownBy(ext::start)
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(ERROR_BOOTSTRAP);

        verify(participantContextService, atLeastOnce()).getParticipantContext(eq(SUPER_USER));
        verify(participantContextService, atLeastOnce()).createParticipantContext(any());
    }

    @Test
    void startShouldRetryWhenVaultSecretsNotReady(ParticipantContextSeedExtension ext,
                                                   ServiceExtensionContext context) {
        // Super user doesn't exist
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(
                        new CreateParticipantContextResponse(GENERATED_API_KEY, null, null)));

        // Vault secrets not available initially, then become available
        when(vault.resolveSecret(anyString()))
                .thenReturn(null)  // First attempt fails
                .thenReturn("secret1")
                .thenReturn("secret2")
                .thenReturn("secret3");

        ext.initialize(context);
        ext.start();

        // Should retry and eventually succeed
        verify(vault, atLeastOnce()).resolveSecret(anyString());
    }

    @Test
    void startShouldFailAfterMaxRetries(ParticipantContextSeedExtension ext,
                                        ServiceExtensionContext context) {
        // Super user doesn't exist
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(
                        new CreateParticipantContextResponse(GENERATED_API_KEY, null, null)));

        // Vault secrets never become available
        when(vault.resolveSecret(anyString())).thenReturn(null);

        ext.initialize(context);

        assertThatThrownBy(ext::start)
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(ERROR_BOOTSTRAP);
    }

    @Test
    void startShouldHandleApiKeyOverride(ParticipantContextSeedExtension ext,
                                         ServiceExtensionContext context) {
        // Configure API key override
        when(context.getSetting(eq(ParticipantContextSeedExtension.SUPERUSER_APIKEY_OVERRIDE_PROPERTY), eq(null)))
                .thenReturn(API_KEY_OVERRIDE);

        // Super user doesn't exist
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(
                        new CreateParticipantContextResponse(GENERATED_API_KEY, null, null)));

        // Vault operations succeed
        when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        ext.initialize(context);
        ext.start();

        verify(vault).storeSecret(eq(SUPERUSER_APIKEY_ALIAS), eq(API_KEY_OVERRIDE));
        verify(monitor, never()).warning(contains("invalid format"));
    }

    @Test
    void startShouldWarnOnInvalidApiKeyOverride(ParticipantContextSeedExtension ext,
                                                 ServiceExtensionContext context) {
        // Configure invalid API key override (missing dot)
        when(context.getSetting(eq(ParticipantContextSeedExtension.SUPERUSER_APIKEY_OVERRIDE_PROPERTY), eq(null)))
                .thenReturn(INVALID_API_KEY);

        // Super user doesn't exist
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(
                        new CreateParticipantContextResponse(GENERATED_API_KEY, null, null)));

        // Vault operations succeed
        when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        ext.initialize(context);
        ext.start();

        verify(vault).storeSecret(eq(SUPERUSER_APIKEY_ALIAS), eq(INVALID_API_KEY));
        verify(monitor).warning(contains("invalid format"));
    }

    @Test
    void startShouldHandleVaultStorageFailure(ParticipantContextSeedExtension ext,
                                              ServiceExtensionContext context) {
        // Configure API key override
        when(context.getSetting(eq(ParticipantContextSeedExtension.SUPERUSER_APIKEY_OVERRIDE_PROPERTY), eq(null)))
                .thenReturn(API_KEY_OVERRIDE);

        // Super user doesn't exist
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.notFound(NOT_FOUND))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Creation succeeds
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(
                        new CreateParticipantContextResponse(GENERATED_API_KEY, null, null)));

        // Vault storage fails
        when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.failure("vault error"));
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        ext.initialize(context);
        ext.start();

        verify(vault).storeSecret(eq(SUPERUSER_APIKEY_ALIAS), eq(API_KEY_OVERRIDE));
        verify(monitor).warning(contains("Error storing API key override"));
    }

    @Test
    void nameShouldReturnExtensionName(ParticipantContextSeedExtension ext) {
        assertThat(ext.name()).isEqualTo(ParticipantContextSeedExtension.EXTENSION_NAME);
    }

    @Test
    void startShouldVerifyAllThreeVaultSecrets(ParticipantContextSeedExtension ext,
                                               ServiceExtensionContext context) {
        // Super user exists
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // All three vault secrets present
        when(vault.resolveSecret(eq(SUPERUSER_APIKEY_ALIAS))).thenReturn("api-key-secret");
        when(vault.resolveSecret(eq(SUPERUSER_ALIAS))).thenReturn("private-key-secret");
        when(vault.resolveSecret(eq(SUPERUSER_STS_CLIENT_SECRET))).thenReturn("sts-secret");

        ext.initialize(context);
        ext.start();

        verify(vault).resolveSecret(eq(SUPERUSER_APIKEY_ALIAS));
        verify(vault).resolveSecret(eq(SUPERUSER_ALIAS));
        verify(vault).resolveSecret(eq(SUPERUSER_STS_CLIENT_SECRET));
    }

    @Test
    void startShouldFailWhenAnyVaultSecretMissing(ParticipantContextSeedExtension ext,
                                                   ServiceExtensionContext context) {
        // Super user exists
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // Only 2 out of 3 vault secrets present
        when(vault.resolveSecret(eq(SUPERUSER_APIKEY_ALIAS))).thenReturn("api-key-secret");
        when(vault.resolveSecret(eq(SUPERUSER_ALIAS))).thenReturn("private-key-secret");
        when(vault.resolveSecret(eq(SUPERUSER_STS_CLIENT_SECRET))).thenReturn(null);  // Missing

        ext.initialize(context);

        assertThatThrownBy(ext::start)
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(ERROR_BOOTSTRAP);
    }

    @Test
    void startShouldLogDetailedVaultSummary(ParticipantContextSeedExtension ext,
                                            ServiceExtensionContext context) {
        // Super user exists
        when(participantContextService.getParticipantContext(eq(SUPER_USER)))
                .thenReturn(ServiceResult.success(createMockParticipantContext()));

        // All vault secrets present
        when(vault.resolveSecret(anyString())).thenReturn(SECRET_VALUE);

        ext.initialize(context);
        ext.start();

        // Verify logging contains summary information
        verify(monitor, atLeastOnce()).info(contains("Vault verification summary"));
        verify(monitor, atLeastOnce()).info(contains("Secrets found: 3/3"));
    }

    private ParticipantContext createMockParticipantContext() {
        return Builder.newInstance()
                .participantId(SUPER_USER)
                .did(SUPER_USER_DID)
                                .apiTokenAlias(SUPERUSER_APIKEY_ALIAS)
                .build();
    }
}
