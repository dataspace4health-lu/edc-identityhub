package com.nttdata.dataspace.ih.services;

import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.identityhub.api.verifiablecredential.validation.ParticipantManifestValidator;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.http.spi.EdcHttpClient;

public interface ParticipantService {
    /**
     * Create a new participant in the system.
     * @param participantData
     * @param participantContextService the injected variable from EDC
     * @param monitor the injected variable from EDC
     * @return
     */
    public ServiceResult<CreateParticipantContextResponse> createParticipant(ParticipantManifest participantData, ParticipantContextService participantContextService, Monitor monitor, EdcHttpClient httpClient);
    
    /**
     * Create a new participant in the system.
     * @param participantData
     * @param participantContextService the injected variable from EDC
     * @param monitor the injected variable from EDC
     * @return
     */
    public ServiceResult<CreateParticipantContextResponse> createParticipant(ParticipantManifest participantData, ParticipantContextService participantContextService, Monitor monitor, ParticipantManifestValidator validator);

}
