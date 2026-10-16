package com.vietnam.pji.services.medical;

import com.vietnam.pji.dto.request.ClinicalAssessmentRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import org.springframework.data.domain.Pageable;

public interface ClinicalAssessmentService {
    ClinicalAssessment create(ClinicalAssessmentRequestDTO data);

    ClinicalAssessment update(Long id, ClinicalAssessmentRequestDTO data);

    ClinicalAssessment getById(Long id);

    void delete(Long id);

    PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable);
}
