package com.vietnam.pji.services.medical;

import com.vietnam.pji.dto.request.AntibioticSusceptibilityRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.model.medical.AntibioticSusceptibility;
import org.springframework.data.domain.Pageable;

public interface AntibioticSusceptibilityService {
    AntibioticSusceptibility create(AntibioticSusceptibilityRequestDTO data);

    AntibioticSusceptibility update(Long id, AntibioticSusceptibilityRequestDTO data);

    AntibioticSusceptibility getById(Long id);

    void delete(Long id);

    PaginationResultDTO getByCulture(Long cultureId, Pageable pageable);
}
