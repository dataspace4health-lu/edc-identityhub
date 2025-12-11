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
public class SuperuserSeedExtension implements ServiceExtension {
    public static final String EXTENSION_NAME = "Superuser Seed Extension";
    public static final String DEFAULT_SUPER_USER_PARTICIPANT_ID = "super-user";
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final long DEFAULT_RETRY_DELAY_MS = 2000;
    
    // Log message constants
    private static final String LOG_SEPARATOR = "========================================";
    private static final String LOG_SUBSEPARATOR = "----------------------------------------";
    
    @Setting(description = "Super-user participant ID", defaultValue = DEFAULT_SUPER_USER_PARTICIPANT_ID)
    public static final String SUPERUSER_PARTICIPANT_ID_PROPERTY = "edc.ih.api.superuser.id";
    
    @Setting(description = "Super-user DID (Decentralized Identifier)", defaultValue = "")
    public static final String SUPERUSER_DID_PROPERTY = "edc.ih.api.superuser.did";
    
    @Setting(description = "Maximum number of retry attempts for super-user bootstrap", defaultValue = "5")
    public static final String MAX_RETRIES_PROPERTY = "edc.ih.api.superuser.max.retries";
    
    @Setting(description = "Delay in milliseconds between retry attempts", defaultValue = "2000")
    public static final String RETRY_DELAY_MS_PROPERTY = "edc.ih.api.superuser.retry.delay.ms";
    
    private String superUserParticipantId;
    private String superUserDid;
    private int maxRetries;
    private long retryDelayMs;
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
        monitor = context.getMonitor().withPrefix("SuperUserSeed");
        
        monitor.info(LOG_SEPARATOR);
        monitor.info("Initializing Superuser Seed Extension");
        monitor.info(LOG_SEPARATOR);
        
        superUserParticipantId = context.getSetting(SUPERUSER_PARTICIPANT_ID_PROPERTY, DEFAULT_SUPER_USER_PARTICIPANT_ID);
        
        superUserDid = context.getSetting(SUPERUSER_DID_PROPERTY, "did:web:%s".formatted(superUserParticipantId));
        
        maxRetries = Integer.parseInt(context.getSetting(MAX_RETRIES_PROPERTY, String.valueOf(DEFAULT_MAX_RETRIES)));
        retryDelayMs = Long.parseLong(context.getSetting(RETRY_DELAY_MS_PROPERTY, String.valueOf(DEFAULT_RETRY_DELAY_MS)));
        
        monitor.info("Configuration: max retries=%d, retry delay=%dms".formatted(maxRetries, retryDelayMs));
    }

    @Override
    public void start() {        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            
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
        if (attempt < maxRetries) {
            monitor.info("Waiting %dms before retry...".formatted(retryDelayMs));
            try {
                Thread.sleep(retryDelayMs);
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
        monitor.severe("✗ CRITICAL: Failed to bootstrap super-user after %d attempts!".formatted(maxRetries));
        monitor.severe("Check database connectivity, HashiCorp Vault, and permissions");
        throw new EdcException("Failed to bootstrap super-user after " + maxRetries + " attempts");
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
            monitor.debug("API key generated with length: %d".formatted(generatedKey.apiKey().length()));
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
        
        var apiKeyAlias = participantContext.getApiTokenAlias();
        var privateKeyAlias = "%s-alias".formatted(superUserParticipantId);
        var stsSecretAlias = "%s-sts-client-secret".formatted(superUserParticipantId);
        
        int secretsFound = 0;
        secretsFound += checkVaultSecret(apiKeyAlias, "API Key") ? 1 : 0;
        secretsFound += checkVaultSecret(privateKeyAlias, "Private Key") ? 1 : 0;
        secretsFound += checkVaultSecret(stsSecretAlias, "STS Client Secret") ? 1 : 0;
        
        return logVerificationSummary(secretsFound);
    }
    
    private ParticipantContext retrieveParticipantContext() {
        var result = participantContextService.getParticipantContext(superUserParticipantId);
        
        if (result.failed()) {
            monitor.warning("⚠ Failed to retrieve participant context");
            monitor.warning("Error: %s".formatted(result.getFailureDetail()));
            return null;
        }
        
        return result.getContent();
    }
    
    /**
     * Generic method to check if a vault secret exists.
     * @param secretAlias the alias/key of the secret in the vault
     * @param secretName human-readable name for logging purposes
     * @return true if secret exists and is not empty, false otherwise
     */
    private boolean checkVaultSecret(String secretAlias, String secretName) {
        monitor.debug("Checking vault secret: %s (alias: %s)".formatted(secretName, secretAlias));
        var secret = vault.resolveSecret(secretAlias);
        
        if (secret == null || secret.isEmpty()) {
            monitor.warning("  ✗ %s NOT FOUND in vault (alias: %s)".formatted(secretName, secretAlias));
            return false;
        }
        
        monitor.debug("  ✓ %s found in vault".formatted(secretName));
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
