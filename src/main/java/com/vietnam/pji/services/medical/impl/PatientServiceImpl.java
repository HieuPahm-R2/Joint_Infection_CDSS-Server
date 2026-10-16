package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.PatientRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.Patient;
import com.vietnam.pji.repository.PatientRepository;
import com.vietnam.pji.services.medical.PatientService;
import com.vietnam.pji.utils.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @Override
    public Patient create(PatientRequestDTO data) {
        if (data.getIdentityCard() != null && patientRepository.existsByIdentityCard(data.getIdentityCard())) {
            throw new InvalidDataException("Patient with this identity card already exists.");
        }
        Patient patient = patientMapper.toEntity(data);
        return patientRepository.save(patient);
    }

    @Override
    public Patient update(Long id, PatientRequestDTO data) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        patientMapper.update(data, patient);
        return patientRepository.save(patient);
    }

    @Override
    public Patient getById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    @Override
    public void delete(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        patientRepository.deleteById(id);
    }

    @Override
    public PaginationResultDTO getAll(Specification<Patient> spec, Pageable pageable) {
        Page<Patient> page = patientRepository.findAll(spec, pageable);
        return PaginationResultDTO.of(page);
    }
}
