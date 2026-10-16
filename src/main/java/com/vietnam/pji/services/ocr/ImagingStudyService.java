package com.vietnam.pji.services.ocr;

import com.vietnam.pji.dto.request.ImagingStudyRequestDTO;
import com.vietnam.pji.dto.response.ImagingStudyResponseDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import org.springframework.data.domain.Pageable;

public interface ImagingStudyService {
    ImagingStudyResponseDTO create(ImagingStudyRequestDTO data);

    ImagingStudyResponseDTO update(Long id, ImagingStudyRequestDTO data);

    ImagingStudyResponseDTO getById(Long id);

    void delete(Long id);

    PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable);
}
