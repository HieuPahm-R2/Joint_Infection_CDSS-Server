package com.vietnam.pji.services.medical;

import com.vietnam.pji.dto.request.LabReportRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.model.medical.LabReport;
import org.springframework.data.domain.Pageable;

public interface LabReportService {
    LabReport create(LabReportRequestDTO data);

    LabReport update(Long id, LabReportRequestDTO data);

    LabReport getById(Long id);

    void delete(Long id);

    PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable);
}
