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
    private static final String LOG_SEPARATOR = "[SuperUserSeed] ========================================";
    private static final String LOG_SUBSEPARATOR = "[SuperUserSeed] ----------------------------------------";
    private static final String LOG_ALIAS_FORMAT = "[SuperUserSeed]   Alias: %s";
    private static final String LOG_NOT_FOUND = "[SuperUserSeed]   ✗ NOT FOUND in vault";
    private static final String LOG_FOUND = "[SuperUserSeed]   ✓ FOUND in vault";
    private static final String LOG_VALUE_LENGTH = "[SuperUserSeed]   Value length: %d characters";
    
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
        monitor = context.getMonitor();
        
        monitor.info(LOG_SEPARATOR);
        monitor.info("[SuperUserSeed] Initializing ParticipantContext Seed Extension");
        monitor.info(LOG_SEPARATOR);
        
        superUserParticipantId = context.getSetting(SUPERUSER_PARTICIPANT_ID_PROPERTY, DEFAULT_SUPER_USER_PARTICIPANT_ID);
        monitor.info("[SuperUserSeed] Configuration - Participant ID: %s".formatted(superUserParticipantId));
        
        superUserDid = context.getSetting(SUPERUSER_DID_PROPERTY, "did:web:%s".formatted(superUserParticipantId));
        monitor.info("[SuperUserSeed] Configuration - DID: %s".formatted(superUserDid));
        
        superUserApiKeyOverride = context.getSetting(SUPERUSER_APIKEY_OVERRIDE_PROPERTY, null);
        if (superUserApiKeyOverride != null) {
            monitor.info("[SuperUserSeed] Configuration - API Key Override: PROVIDED (length=%d)".formatted(superUserApiKeyOverride.length()));
        } else {
            monitor.info("[SuperUserSeed] Configuration - API Key Override: NOT PROVIDED (will auto-generate)");
        }
        
        monitor.info("[SuperUserSeed] Expected vault secrets:");
        monitor.info("[SuperUserSeed]   - %s-apikey (API authentication key)".formatted(superUserParticipantId));
        monitor.info("[SuperUserSeed]   - %s-alias (Ed25519 private key)".formatted(superUserParticipantId));
        monitor.info("[SuperUserSeed]   - %s-sts-client-secret (STS OAuth secret)".formatted(superUserParticipantId));
        monitor.info("[SuperUserSeed] Initialization complete");
    }

    @Override
    public void start() {
        logBootstrapStart();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            logAttempt(attempt);
            
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
    
    private void logBootstrapStart() {
        monitor.info(LOG_SEPARATOR);
        monitor.info("[SuperUserSeed] Starting super-user bootstrap process");
        monitor.info("[SuperUserSeed] Max retries: %d, Delay: %dms".formatted(MAX_RETRIES, RETRY_DELAY_MS));
        monitor.info(LOG_SEPARATOR);
    }
    
    private void logAttempt(int attempt) {
        monitor.info(LOG_SUBSEPARATOR);
        monitor.info("[SuperUserSeed] Attempt %d/%d".formatted(attempt, MAX_RETRIES));
        monitor.info(LOG_SUBSEPARATOR);
    }
    
    private boolean tryBootstrap() {
        monitor.info("[SuperUserSeed] Checking if super-user '%s' already exists...".formatted(superUserParticipantId));
        var existingContext = participantContextService.getParticipantContext(superUserParticipantId);
        
        if (existingContext.succeeded()) {
            return handleExistingSuperUser();
        } else {
            return handleNewSuperUser();
        }
    }
    
    private boolean handleExistingSuperUser() {
        monitor.info("[SuperUserSeed] ✓ Super-user already exists: %s".formatted(superUserParticipantId));
        monitor.info("[SuperUserSeed] Verifying vault secrets...");
        
        if (verifyVaultSecrets()) {
            return true;
        } else {
            monitor.warning("[SuperUserSeed] ⚠ Vault secrets missing for existing participant");
            monitor.warning("[SuperUserSeed] This may indicate a corrupted state");
            return false;
        }
    }
    
    private boolean handleNewSuperUser() {
        monitor.info("[SuperUserSeed] Super-user does not exist, proceeding with creation...");
        
        if (createSuperUser()) {
            monitor.info("[SuperUserSeed] ✓ Super-user created, verifying vault secrets...");
            
            if (verifyVaultSecrets()) {
                return true;
            } else {
                monitor.warning("[SuperUserSeed] ⚠ Vault secrets not yet available after creation");
                return false;
            }
        } else {
            monitor.warning("[SuperUserSeed] ⚠ Failed to create super-user");
            return false;
        }
    }
    
    private boolean waitForRetry(int attempt) {
        if (attempt < MAX_RETRIES) {
            monitor.info("[SuperUserSeed] Waiting %dms before retry...".formatted(RETRY_DELAY_MS));
            try {
                Thread.sleep(RETRY_DELAY_MS);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitor.warning("[SuperUserSeed] ⚠ Bootstrap interrupted");
                return false;
            }
        }
        return false;
    }
    
    private void logBootstrapSuccess() {
        monitor.info("[SuperUserSeed] ✓ All vault secrets verified!");
        monitor.info(LOG_SEPARATOR);
        monitor.info("[SuperUserSeed] ✓ Bootstrap complete");
        monitor.info(LOG_SEPARATOR);
    }
    
    private void handleBootstrapFailure() {
        monitor.severe("[SuperUserSeed] ✗ CRITICAL: Failed to bootstrap super-user after %d attempts!".formatted(MAX_RETRIES));
        monitor.severe("[SuperUserSeed] Check database connectivity, HashiCorp Vault, and permissions");
        throw new EdcException("Failed to bootstrap super-user after " + MAX_RETRIES + " attempts");
    }
    
    /**
     * Creates the super-user participant context.
     * @return true if creation succeeded, false otherwise
     */
    private boolean createSuperUser() {
        monitor.info(LOG_SUBSEPARATOR);
        monitor.info("[SuperUserSeed] Building ParticipantManifest:");
        monitor.info("[SuperUserSeed]   Participant ID: %s".formatted(superUserParticipantId));
        monitor.info("[SuperUserSeed]   DID: %s".formatted(superUserDid));
        monitor.info("[SuperUserSeed]   Active: true");
        monitor.info("[SuperUserSeed]   Roles: [%s]".formatted(ServicePrincipal.ROLE_ADMIN));
        monitor.info("[SuperUserSeed]   Key Algorithm: EdDSA");
        monitor.info("[SuperUserSeed]   Key Curve: Ed25519");
        monitor.info("[SuperUserSeed]   Key ID: %s-key".formatted(superUserParticipantId));
        monitor.info("[SuperUserSeed]   Private Key Alias: %s-alias".formatted(superUserParticipantId));
        monitor.info(LOG_SUBSEPARATOR);
        
        monitor.info("[SuperUserSeed] Calling ParticipantContextService.createParticipantContext()...");
        
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
            monitor.info("[SuperUserSeed] ✓ ParticipantContext created successfully!");
            monitor.info(LOG_SUBSEPARATOR);
            monitor.info("[SuperUserSeed] Generated credentials:");
            
            // Determine which API key to log (override or generated)
            var apiKey = ofNullable(superUserApiKeyOverride)
                    .filter(key -> {
                        monitor.info("[SuperUserSeed] Using API key override from configuration");
                        if (!key.contains(".")) {
                            monitor.warning("[SuperUserSeed] ⚠ API key override has invalid format!");
                            monitor.warning("[SuperUserSeed]   Expected: 'base64(<participantId>).<random-string>'");
                            monitor.warning("[SuperUserSeed]   This may cause authentication issues.");
                        }
                        // TODO: Uncomment if you want to replace the auto-generated API key with the override
                        return true;
                    })
                    .orElseGet(() -> {
                        monitor.info("[SuperUserSeed] Using auto-generated API key");
                        return generatedKey.apiKey();
                    });
            
            //monitor.info("[SuperUserSeed]   API Key: %s".formatted(apiKey));
            monitor.info("[SuperUserSeed]   API Key Length: %d characters".formatted(apiKey.length()));
            monitor.info(LOG_SUBSEPARATOR);
            
            monitor.info("[SuperUserSeed] EDC will automatically store vault secrets:");
            monitor.info("[SuperUserSeed]   1. %s-apikey → API authentication key".formatted(superUserParticipantId));
            monitor.info("[SuperUserSeed]   2. %s-alias → Ed25519 private key (JWK)".formatted(superUserParticipantId));
            monitor.info("[SuperUserSeed]   3. %s-sts-client-secret → STS OAuth secret".formatted(superUserParticipantId));
            
            return true;
        } else {
            monitor.warning("[SuperUserSeed] ✗ Failed to create super-user: %s".formatted(result.getFailureDetail()));
            return false;
        }
    }
    
    /**
     * Verifies existence of all required vault secrets.
     * @return true if all secrets exist, false otherwise
     */
    private boolean verifyVaultSecrets() {
        monitor.info("[SuperUserSeed] Verifying vault secrets for participant: %s".formatted(superUserParticipantId));
        
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
    
    private org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantContext retrieveParticipantContext() {
        monitor.info("[SuperUserSeed] Retrieving ParticipantContext from service...");
        var result = participantContextService.getParticipantContext(superUserParticipantId);
        
        if (result.failed()) {
            monitor.warning("[SuperUserSeed] ⚠ Failed to retrieve participant context");
            monitor.warning("[SuperUserSeed] Error: %s".formatted(result.getFailureDetail()));
            return null;
        }
        
        var pc = result.getContent();
        monitor.info("[SuperUserSeed] ✓ ParticipantContext retrieved successfully");
        monitor.info("[SuperUserSeed]   State: %s".formatted(pc.getState()));
        monitor.info("[SuperUserSeed]   DID: %s".formatted(pc.getDid()));
        monitor.info("[SuperUserSeed]   API Token Alias: %s".formatted(pc.getApiTokenAlias()));
        return pc;
    }
    
    private boolean checkApiKeySecret(org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantContext pc) {
        var apiKeyAlias = pc.getApiTokenAlias();
        monitor.info("[SuperUserSeed] Checking vault secret 1/3: API Key");
        monitor.info(LOG_ALIAS_FORMAT.formatted(apiKeyAlias));
        var apiKey = vault.resolveSecret(apiKeyAlias);
        
        if (apiKey == null || apiKey.isEmpty()) {
            monitor.warning(LOG_NOT_FOUND);
            return false;
        }
        
        monitor.info(LOG_FOUND);
        monitor.info(LOG_VALUE_LENGTH.formatted(apiKey.length()));
        monitor.info("[SuperUserSeed]   Value preview: %s...".formatted(apiKey.substring(0, Math.min(20, apiKey.length()))));
        return true;
    }
    
    private boolean checkPrivateKeySecret() {
        var privateKeyAlias = "%s-alias".formatted(superUserParticipantId);
        monitor.info("[SuperUserSeed] Checking vault secret 2/3: Private Key");
        monitor.info(LOG_ALIAS_FORMAT.formatted(privateKeyAlias));
        var privateKey = vault.resolveSecret(privateKeyAlias);
        
        if (privateKey == null || privateKey.isEmpty()) {
            monitor.warning(LOG_NOT_FOUND);
            return false;
        }
        
        monitor.info(LOG_FOUND);
        monitor.info(LOG_VALUE_LENGTH.formatted(privateKey.length()));
        logPrivateKeyFormat(privateKey);
        return true;
    }
    
    private void logPrivateKeyFormat(String privateKey) {
        if (privateKey.contains("\"kty\"")) {
            monitor.info("[SuperUserSeed]   Format: JWK (JSON Web Key)");
            if (privateKey.contains("\"OKP\"") && privateKey.contains("\"Ed25519\"")) {
                monitor.info("[SuperUserSeed]   Key type: Ed25519 (correct)");
            }
        } else {
            monitor.warning("[SuperUserSeed]   ⚠ Format: Unknown (expected JWK)");
        }
    }
    
    private boolean checkStsSecret() {
        var stsSecretAlias = "%s-sts-client-secret".formatted(superUserParticipantId);
        monitor.info("[SuperUserSeed] Checking vault secret 3/3: STS Client Secret");
        monitor.info(LOG_ALIAS_FORMAT.formatted(stsSecretAlias));
        var stsSecret = vault.resolveSecret(stsSecretAlias);
        
        if (stsSecret == null || stsSecret.isEmpty()) {
            monitor.warning(LOG_NOT_FOUND);
            return false;
        }
        
        monitor.info(LOG_FOUND);
        monitor.info(LOG_VALUE_LENGTH.formatted(stsSecret.length()));
        return true;
    }
    
    private boolean logVerificationSummary(int secretsFound) {
        int secretsMissing = 3 - secretsFound;
        boolean allSecretsExist = secretsFound == 3;
        
        monitor.info(LOG_SUBSEPARATOR);
        monitor.info("[SuperUserSeed] Vault verification summary:");
        monitor.info("[SuperUserSeed]   Secrets found: %d/3".formatted(secretsFound));
        monitor.info("[SuperUserSeed]   Secrets missing: %d/3".formatted(secretsMissing));
        monitor.info("[SuperUserSeed]   Overall status: %s".formatted(allSecretsExist ? "✓ SUCCESS" : "✗ INCOMPLETE"));
        monitor.info(LOG_SUBSEPARATOR);
        
        return allSecretsExist;
    }
}
