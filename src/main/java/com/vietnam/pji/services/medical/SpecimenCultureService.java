package com.vietnam.pji.services.medical;

import com.vietnam.pji.dto.request.SpecimenCultureRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.model.medical.SpecimenCulture;
import org.springframework.data.domain.Pageable;

public interface SpecimenCultureService {
    SpecimenCulture create(SpecimenCultureRequestDTO data);

    SpecimenCulture update(Long id, SpecimenCultureRequestDTO data);

    SpecimenCulture getById(Long id);

    void delete(Long id);

    PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable);
}
