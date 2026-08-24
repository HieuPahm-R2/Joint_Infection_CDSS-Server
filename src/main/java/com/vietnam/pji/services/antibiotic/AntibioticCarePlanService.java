package com.vietnam.pji.services.antibiotic;

import com.vietnam.pji.dto.response.AntibioticCarePlanResponseDTO;

public interface AntibioticCarePlanService {
    AntibioticCarePlanResponseDTO generate(Long episodeId);
}
