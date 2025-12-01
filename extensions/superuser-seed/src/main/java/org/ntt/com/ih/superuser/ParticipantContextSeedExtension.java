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
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantContext;
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
 * - Creates super-user if not exists (only one super-user per deployment)
 * - Verifies vault secrets are automatically created by ParticipantContextService
 * - Retrieves and logs API key from Vault with retry logic
 * 
 * Note: Only one super-user should exist per deployment. If configuration changes
 * (edc.ih.api.superuser.id), the extension will detect existing super-user and skip creation.
 * 
 * Vault secrets created automatically by EDC:
 * - {participantId}-apikey: API authentication key
 * - {participantId}-alias: Ed25519 private key (JWK format)
 * - {participantId}-sts-client-secret: STS OAuth client secret
 */
public class ParticipantContextSeedExtension implements ServiceExtension {
    public static final String EXTENSION_NAME = "ParticipantContext Seed Extension";
    public static final String DEFAULT_SUPER_USER_PARTICIPANT_ID = "super-user";
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2000;
    
    // Log message constants
    private static final String LOG_SEPARATOR = "========================================";
    private static final String LOG_SUBSEPARATOR = "----------------------------------------";
    private static final String LOG_NOT_FOUND = "  ✗ NOT FOUND in vault";
    
    @Setting(description = "Super-user participant ID", defaultValue = DEFAULT_SUPER_USER_PARTICIPANT_ID)
    public static final String SUPERUSER_PARTICIPANT_ID_PROPERTY = "edc.ih.api.superuser.id";
    
    @Setting(description = "Super-user DID (Decentralized Identifier)", defaultValue = "")
    public static final String SUPERUSER_DID_PROPERTY = "edc.ih.api.superuser.did";
    
    @Setting(description = "Explicitly set the initial API key for the super-user")
    public static final String SUPERUSER_APIKEY_OVERRIDE_PROPERTY = "edc.ih.api.superuser.key";
    
    private String superUserParticipantId;
    private String superUserDid;
    private String superUserApiKeyOverride;
    private Monitor monitor;
    
    @Inject
    private ParticipantContextService participantContextService;
    
    @Inject
    private Vault vault;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor().withPrefix("[SuperUserSeed] ");
        
        monitor.info(LOG_SEPARATOR);
        monitor.info("Initializing ParticipantContext Seed Extension");
        monitor.info(LOG_SEPARATOR);
        
        superUserParticipantId = context.getSetting(SUPERUSER_PARTICIPANT_ID_PROPERTY, DEFAULT_SUPER_USER_PARTICIPANT_ID);
        
        superUserDid = context.getSetting(SUPERUSER_DID_PROPERTY, "did:web:%s".formatted(superUserParticipantId));
        
        superUserApiKeyOverride = context.getSetting(SUPERUSER_APIKEY_OVERRIDE_PROPERTY, null);
        if (superUserApiKeyOverride != null) {
            monitor.info("Configuration - API Key Override: PROVIDED (length=%d)".formatted(superUserApiKeyOverride.length()));
        } else {
            monitor.info("Configuration - API Key Override: NOT PROVIDED (will auto-generate)");
        }
    }

    @Override
    public void start() {        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            
            if (tryBootstrap()) {
                logBootstrapSuccess();
                return;
            }
            
            if (!waitForRetry(attempt)) {
                return;
            }
        }
        
        handleBootstrapFailure();
    }   
    
    private boolean tryBootstrap() {
        monitor.info("Checking if super-user '%s' already exists...".formatted(superUserParticipantId));
        var existingContext = participantContextService.getParticipantContext(superUserParticipantId);
        
        if (existingContext.succeeded()) {
            return handleExistingSuperUser();
        } else {
            return handleNewSuperUser();
        }
    }
    
    private boolean handleExistingSuperUser() {
        monitor.info("✓ Super-user already exists: %s".formatted(superUserParticipantId));
        monitor.info("Verifying vault secrets...");
        
        if (verifyVaultSecrets()) {
            return true;
        } else {
            monitor.warning("⚠ Vault secrets missing for existing participant");
            monitor.warning("This may indicate a corrupted state");
            return false;
        }
    }
    
    private boolean handleNewSuperUser() {
        monitor.info("Super-user does not exist, proceeding with creation...");
        
        if (createSuperUser()) {
            monitor.info("✓ Super-user created, verifying vault secrets...");
            
            if (verifyVaultSecrets()) {
                return true;
            } else {
                monitor.warning("⚠ Vault secrets not yet available after creation");
                return false;
            }
        } else {
            monitor.warning("⚠ Failed to create super-user");
            return false;
        }
    }
    
    private boolean waitForRetry(int attempt) {
        if (attempt < MAX_RETRIES) {
            monitor.info("Waiting %dms before retry...".formatted(RETRY_DELAY_MS));
            try {
                Thread.sleep(RETRY_DELAY_MS);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitor.warning("⚠ Bootstrap interrupted");
                return false;
            }
        }
        return false;
    }
    
    private void logBootstrapSuccess() {
        monitor.info("✓ All vault secrets verified!");
        monitor.info(LOG_SEPARATOR);
        monitor.info("✓ Bootstrap complete");
        monitor.info(LOG_SEPARATOR);
    }
    
    private void handleBootstrapFailure() {
        monitor.severe("✗ CRITICAL: Failed to bootstrap super-user after %d attempts!".formatted(MAX_RETRIES));
        monitor.severe("Check database connectivity, HashiCorp Vault, and permissions");
        throw new EdcException("Failed to bootstrap super-user after " + MAX_RETRIES + " attempts");
    }
    
    /**
     * Creates the super-user participant context.
     * @return true if creation succeeded, false otherwise
     */
    private boolean createSuperUser() {
        var result = participantContextService.createParticipantContext(
                ParticipantManifest.Builder.newInstance()
                        .participantId(superUserParticipantId)
                        .did(superUserDid)
                        .active(true)
                        .key(KeyDescriptor.Builder.newInstance()
                                .keyGeneratorParams(Map.of("algorithm", "EdDSA", "curve", "Ed25519"))
                                .keyId("%s-key".formatted(superUserParticipantId))
                                .privateKeyAlias("%s-alias".formatted(superUserParticipantId))
                                .build())
                        .roles(List.of(ServicePrincipal.ROLE_ADMIN))
                        .build());
        
        if (result.succeeded()) {
            var generatedKey = result.getContent();
            monitor.info(LOG_SUBSEPARATOR);            
            // Determine which API key to log (override or generated)
            // TODO: Remove this section in production if you want to disable the ability to
            // override the superUserApiKey. Commenting this will ensure that this value
            // cannot be modified through configuration.
            var apiKey = ofNullable(superUserApiKeyOverride)
                    .filter(key -> {
                        if (!key.contains(".")) {
                            monitor.warning("⚠ API key override has invalid format!");
                            monitor.warning("  Expected: 'base64(<participantId>).<random-string>'");
                            monitor.warning("  This may cause authentication issues.");
                        }
                        // Store the override key in vault (replaces auto-generated key)
                        participantContextService.getParticipantContext(superUserParticipantId)
                                .onSuccess(pc -> vault.storeSecret(pc.getApiTokenAlias(), key)
                                        .onSuccess(u -> monitor.info("✓ API key override stored in vault"))
                                        .onFailure(f -> monitor.warning("⚠ Error storing API key override: %s".formatted(f.getFailureDetail()))))
                                .onFailure(f -> monitor.warning("⚠ Error retrieving participant for API key override: %s".formatted(f.getFailureDetail())));
                        return true;
                    })
                    .orElseGet(() -> {
                        return generatedKey.apiKey();
                    });            
            return true;
        } else {
            monitor.warning("✗ Failed to create super-user: %s".formatted(result.getFailureDetail()));
            return false;
        }
    }
    
    /**
     * Verifies existence of all required vault secrets.
     * @return true if all secrets exist, false otherwise
     */
    private boolean verifyVaultSecrets() {        
        var participantContext = retrieveParticipantContext();
        if (participantContext == null) {
            return false;
        }
        
        int secretsFound = 0;
        secretsFound += checkApiKeySecret(participantContext) ? 1 : 0;
        secretsFound += checkPrivateKeySecret() ? 1 : 0;
        secretsFound += checkStsSecret() ? 1 : 0;
        
        return logVerificationSummary(secretsFound);
    }
    
    private ParticipantContext retrieveParticipantContext() {
        var result = participantContextService.getParticipantContext(superUserParticipantId);
        
        if (result.failed()) {
            monitor.warning("⚠ Failed to retrieve participant context");
            monitor.warning("Error: %s".formatted(result.getFailureDetail()));
            return null;
        }
        
        var pc = result.getContent();
        return pc;
    }
    
    private boolean checkApiKeySecret(ParticipantContext pc) {
        var apiKeyAlias = pc.getApiTokenAlias();
        var apiKey = vault.resolveSecret(apiKeyAlias);
        
        if (apiKey == null || apiKey.isEmpty()) {
            monitor.warning(LOG_NOT_FOUND);
            return false;
        }
        return true;
    }
    
    private boolean checkPrivateKeySecret() {
        var privateKeyAlias = "%s-alias".formatted(superUserParticipantId);
        var privateKey = vault.resolveSecret(privateKeyAlias);
        
        if (privateKey == null || privateKey.isEmpty()) {
            monitor.warning(LOG_NOT_FOUND);
            return false;
        }
        return true;
    }
    
    private boolean checkStsSecret() {
        var stsSecretAlias = "%s-sts-client-secret".formatted(superUserParticipantId);
        var stsSecret = vault.resolveSecret(stsSecretAlias);
        
        if (stsSecret == null || stsSecret.isEmpty()) {
            monitor.warning(LOG_NOT_FOUND);
            return false;
        }
        return true;
    }
    
    private boolean logVerificationSummary(int secretsFound) {
        int secretsMissing = 3 - secretsFound;
        boolean allSecretsExist = secretsFound == 3;
        
        monitor.info(LOG_SUBSEPARATOR);
        monitor.info("Vault verification summary:");
        monitor.info("  Secrets found: %d/3".formatted(secretsFound));
        monitor.info("  Secrets missing: %d/3".formatted(secretsMissing));
        monitor.info("  Overall status: %s".formatted(allSecretsExist ? "✓ SUCCESS" : "✗ INCOMPLETE"));
        monitor.info(LOG_SUBSEPARATOR);
        
        return allSecretsExist;
    }
}
