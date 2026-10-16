package com.vietnam.pji.services.episode;

import com.vietnam.pji.dto.request.AdmissionRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.model.medical.Admission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface AdmissionService {
    Admission create(AdmissionRequestDTO data);

    Admission update(Long id, AdmissionRequestDTO data);

    Admission getById(Long id);

    void delete(Long id);

    PaginationResultDTO getAll(Specification<Admission> spec, Pageable pageable);

    PaginationResultDTO getByPatient(Long patientId, Pageable pageable);
}
