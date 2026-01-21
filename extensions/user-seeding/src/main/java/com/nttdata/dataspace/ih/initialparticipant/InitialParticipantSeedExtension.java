package com.nttdata.dataspace.ih.initialparticipant;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.identityhub.api.verifiablecredential.validation.ParticipantManifestValidator;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import java.util.List;
import java.util.Map;

import com.nttdata.dataspace.ih.manageparticipant.ParticipantServiceImpl;
import com.nttdata.dataspace.ih.manageparticipant.ParticipantConstants;


/**
 * Extension to seed initial participants into the system.
 */
public class InitialParticipantSeedExtension implements ServiceExtension {

    @Inject
    Monitor monitor;

    @Inject
    private ParticipantContextService participantContextService;

    private String[] participantIds;
    private String keyAlgo;
    private String keyCurve;

    ParticipantManifestValidator validator;
    

    @Override
    public void initialize(ServiceExtensionContext context) {
        // Initialization logic here   
        monitor = context.getMonitor().withPrefix("InitialParticipantsSeed");
        monitor.debug("Initial Participants Seed Extension initialized.");

        validator = new ParticipantManifestValidator(monitor);
        
        // Retrieve the ParticipantContextService from the context
        participantContextService = context.getService(ParticipantContextService.class);

        //if no participantIds provided, system will throw an exception
        participantIds = context.getConfig().getString(ParticipantConstants.PARTICIPANT_ID_KEY).split(",");
        keyAlgo = context.getConfig().getString(ParticipantConstants.SIGN_PC_ALGO_KEY, ParticipantConstants.SIGN_SCHEME_EDDSA);
        keyCurve = context.getConfig().getString(ParticipantConstants.SIGN_PC_CURVE_KEY, ParticipantConstants.SIGN_SCHEME_ED25519);
    }

    @Override
    public void start() {
        monitor.info("Starting Initial Participant Seeding...");
        
        ParticipantServiceImpl participantServiceImpl = new ParticipantServiceImpl();

         ParticipantServiceImpl participantService = participantServiceImpl;
        for (String participantId : participantIds) {
            participantId = participantId.trim();
            if (participantId.isEmpty()) {
                continue; // skip empty IDs
            }
            monitor.info("Seeding initial participant with ID: " + participantId);

            ParticipantManifest participantManifest = ParticipantManifest.Builder.newInstance()
                        .participantContextId(participantId)
                        .did(ParticipantConstants.PARTICIPANT_DID_FORMAT_STRING.formatted(participantId))
                        .active(true)
                        .key(KeyDescriptor.Builder.newInstance()
                                .keyGeneratorParams(Map.of(ParticipantConstants.KEY_ALGO_STRING, keyAlgo, ParticipantConstants.KEY_CURVE_STRING, keyCurve))
                                .keyId(ParticipantConstants.PARTICIPANT_PUBLIC_KEY_ALIAS_FORMAT.formatted(participantId))
                                .privateKeyAlias(ParticipantConstants.PARTICIPANT_PRIVATE_KEY_ALIAS.formatted(participantId))
                                .build())
                        .roles(List.of())
                        .build();

            participantService.createParticipant(participantManifest, participantContextService, monitor, validator);
        }

    }

}
