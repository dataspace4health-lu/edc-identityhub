package com.nttdata.dataspace.ih.manageparticipant;

import com.nttdata.dataspace.ih.services.ParticipantService;

import org.eclipse.edc.identityhub.api.verifiablecredential.validation.ParticipantManifestValidator;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;


import org.eclipse.edc.http.spi.EdcHttpClient;

/**
 * Implementation of ParticipantService interface.
 */
public class ParticipantServiceImpl implements ParticipantService {

    /**
     * Create a new participant in the system.
     * @param participantData
     * @param participantContextService the injected variable from EDC
     * @param monitor the injected variable from EDC
     * @param EdcHttpClient incase if we want to use the client and call the provided api by identity hub (which is currectly v1alpha)
     */
    @Override
    public ServiceResult<CreateParticipantContextResponse> createParticipant(ParticipantManifest participantData, ParticipantContextService participantContextService, Monitor monitor,  EdcHttpClient httpClient) {
        /**
         * Implement the logic to call rest end point provided by Identity hub . The api does the validation but does it check if the participant already exist?
         * ParticipantContants.CREATE_PARTICIPANT_EP 
         * in this approach we need the superuser api key 
         * 1. to be fetched and sent in the request if we want to create the participant interanlly
         * 2. should be provided to the superuser who can trigger the request directly to the endpoint provided by IH and this extension is not called in that case
         */
        return null;
    }

     /**
     * Create a new participant in the system.
     * @param participantData
     * @param participantContextService the injected variable from EDC
     * @param monitor the injected variable from EDC
     */
    @Override
    public ServiceResult<CreateParticipantContextResponse> createParticipant(ParticipantManifest participantData, ParticipantContextService participantContextService, Monitor monitor, ParticipantManifestValidator validator, ParticipantContextConfigService participantContextConfigService) {

        String participantId = participantData.getParticipantContextId();

        if (participantContextService.getParticipantContext(participantId).succeeded()) { // already exists
            monitor.info("Participant already exists with ID '%s', will not re-create".formatted(participantId));
            return ServiceResult.conflict(String.format("Participant already exist: %s", participantId));
        }

        if(validator.validate(participantData).succeeded()){

            CreateParticipantContextResponse participantContextResponse = participantContextService.createParticipantContext(participantData).onSuccess( response -> 
                    monitor.info(String.format("Participant created successfully: %s", participantId))
                )
                .orElseThrow(f -> new EdcException(String.format("Error creating participant %s : %s", participantId, f.getFailureDetail())));

            // Save the ParticipantContextConfiguration for this participant
            // This is required for the system to properly handle this participant context
            monitor.debug(String.format("Creating ParticipantContextConfiguration for : %s", participantId));
            var participantContextConfig = ParticipantContextConfiguration.Builder.newInstance()
                    .participantContextId(participantId)
                    .build();
            
            var saveResult = participantContextConfigService.save(participantContextConfig);
            if (saveResult.failed()) {
                throw new EdcException(String.format("Error creating ParticipantContextConfiguration %s : %s", participantId, saveResult.getFailureDetail()));
            } else {
                monitor.info("Successfully saved ParticipantContextConfiguration for participant: " + participantId);
            }
            
        return ServiceResult.success(participantContextResponse);
        }
        
        return ServiceResult.conflict(String.format("Can not create participant with invalid details: %s", String.valueOf(participantData)));

    }
    
}
