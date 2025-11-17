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

import org.eclipse.edc.identityhub.spi.authentication.ServicePrincipal;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * Production-ready super user seed extension with HashiCorp Vault integration.
 * Ensures API keys persist across pod restarts and provides audit trail.
 * 
 * This extension:
 * - Retrieves API key from Vault on startup
 * - Creates super-user if not exists
 * - Stores API key in Vault for persistence
 * - Supports graceful fallback for backward compatibility
 */
public class ParticipantContextSeedExtension implements ServiceExtension {
    public static final String NAME = "ParticipantContext Seed Extension";
    public static final String DEFAULT_SUPER_USER_PARTICIPANT_ID = "super-user";
    
    @Setting(value = "Vault path for super user API key", defaultValue = "super-user-apikey")
    public static final String SUPERUSER_VAULT_APIKEY_PATH = "edc.ih.superuser.vault.apikey-path";
    
    @Setting(value = "Super-user participant ID", defaultValue = DEFAULT_SUPER_USER_PARTICIPANT_ID)
    public static final String SUPERUSER_PARTICIPANT_ID_PROPERTY = "edc.ih.api.superuser.id";
    
    @Setting(value = "Fallback API key from config (deprecated, use Vault)")
    public static final String SUPERUSER_APIKEY_PROPERTY = "edc.ih.api.superuser.key";
    
    private String superUserParticipantId;
    private String vaultApiKeyPath;
    private String fallbackApiKey;
    private Monitor monitor;
    
    @Inject
    private ParticipantContextService participantContextService;
    
    @Inject
    private Vault vault;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        superUserParticipantId = context.getSetting(SUPERUSER_PARTICIPANT_ID_PROPERTY, DEFAULT_SUPER_USER_PARTICIPANT_ID);
        vaultApiKeyPath = context.getSetting(SUPERUSER_VAULT_APIKEY_PATH, "super-user-apikey");
        fallbackApiKey = context.getSetting(SUPERUSER_APIKEY_PROPERTY, null);
        monitor = context.getMonitor();
        
        if (fallbackApiKey != null && !fallbackApiKey.equals("change-me")) {
            monitor.warning("Using fallback API key from configuration. This is deprecated and insecure for production. Please store the API key in Vault instead.");
        }
        
        monitor.info("Vault-backed super-user initialization enabled (ID: %s, Path: %s)".formatted(superUserParticipantId, vaultApiKeyPath));
    }

    @Override
    public void start() {
        // Check if super-user already exists
        if (participantContextService.getParticipantContext(superUserParticipantId).succeeded()) {
            monitor.info("Super-user already exists: " + superUserParticipantId);
            verifyApiKeyInVault();
            return;
        }

        monitor.info("Creating super-user: " + superUserParticipantId);
        createSuperUser();
    }
    
    private void createSuperUser() {
        participantContextService.createParticipantContext(
                ParticipantManifest.Builder.newInstance()
                        .participantId(superUserParticipantId)
                        .did("did:web:%s".formatted(superUserParticipantId))
                        .active(true)
                        .key(KeyDescriptor.Builder.newInstance()
                                .keyGeneratorParams(Map.of("algorithm", "EdDSA", "curve", "Ed25519"))
                                .keyId("%s-key".formatted(superUserParticipantId))
                                .privateKeyAlias("%s-alias".formatted(superUserParticipantId))
                                .build())
                        .roles(List.of(ServicePrincipal.ROLE_ADMIN))
                        .build())
                .onSuccess(generatedKey -> {
                    // Priority: Vault > Config > Generated
                    var vaultApiKey = vault.resolveSecret(vaultApiKeyPath);
                    var apiKey = ofNullable(vaultApiKey)
                            .filter(key -> !key.isEmpty())
                            .or(() -> ofNullable(fallbackApiKey).filter(key -> !key.equals("change-me")))
                            .orElse(generatedKey.apiKey());
                    
                    // Validate format
                    if (!apiKey.contains(".")) {
                        monitor.warning("API key format warning: Expected 'base64(participantId).randomString'. Current format may cause authentication issues.");
                    }
                    
                    // Store in both locations
                    persistToVault(apiKey);
                    participantContextService.getParticipantContext(superUserParticipantId)
                            .onSuccess(pc -> vault.storeSecret(pc.getApiTokenAlias(), apiKey)
                                    .onSuccess(u -> monitor.debug("API key stored in participant alias"))
                                    .onFailure(f -> monitor.warning("Failed to store key in participant alias: " + f.getFailureDetail())))
                            .onFailure(f -> monitor.warning("Failed to retrieve participant context: " + f.getFailureDetail()));
                    
                    monitor.info("Super-user created successfully! Participant ID: %s, API Key: %s".formatted(superUserParticipantId, maskApiKey(apiKey)));
                })
                .orElseThrow(f -> new EdcException("Failed to create super-user: " + f.getFailureDetail()));
    }
    
    private void verifyApiKeyInVault() {
        var vaultKey = vault.resolveSecret(vaultApiKeyPath);
        if (vaultKey != null && !vaultKey.isEmpty()) {
            monitor.debug("API key verified in Vault at: " + vaultApiKeyPath);
        } else {
            monitor.warning("Super-user exists but API key not found in Vault at: %s. Attempting sync from participant alias...".formatted(vaultApiKeyPath));
            participantContextService.getParticipantContext(superUserParticipantId)
                    .onSuccess(pc -> {
                        var aliasKey = vault.resolveSecret(pc.getApiTokenAlias());
                        if (aliasKey != null && !aliasKey.isEmpty()) {
                            persistToVault(aliasKey);
                            monitor.info("Synced API key from alias to vault path: " + vaultApiKeyPath);
                        } else {
                            monitor.severe("CRITICAL: API key not found in participant alias!");
                        }
                    })
                    .onFailure(f -> monitor.warning("Failed to sync API key: " + f.getFailureDetail()));
        }
    }
    
    private void persistToVault(String apiKey) {
        vault.storeSecret(vaultApiKeyPath, apiKey)
                .onSuccess(u -> monitor.info("API key persisted to Vault: " + vaultApiKeyPath))
                .onFailure(f -> monitor.severe("CRITICAL: Failed to persist API key to Vault (Path: %s, Error: %s). API key will be lost on pod restart!".formatted(vaultApiKeyPath, f.getFailureDetail())));
    }
    
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 20) {
            return "***";
        }
        return apiKey.substring(0, 10) + "..." + apiKey.substring(apiKey.length() - 10);
    }
}
