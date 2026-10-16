package com.vietnam.pji.services.episode.impl;

import com.vietnam.pji.dto.request.AdmissionRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.Patient;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.repository.AdmissionRepository;
import com.vietnam.pji.repository.PatientRepository;
import com.vietnam.pji.services.episode.AdmissionCareTeamService;
import com.vietnam.pji.services.episode.AdmissionGraphLoader;
import com.vietnam.pji.services.episode.AdmissionService;
import com.vietnam.pji.services.auth.CurrentActorProvider;
import com.vietnam.pji.utils.mapper.AdmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdmissionServiceImpl implements AdmissionService {

    private final AdmissionRepository episodeRepository;
    private final PatientRepository patientRepository;
    private final AdmissionMapper episodeMapper;
    private final AdmissionGraphLoader episodeGraph;
    private final AdmissionCareTeamService careTeamService;
    private final CurrentActorProvider actorProvider;

    @Override
    @Transactional
    public Admission create(AdmissionRequestDTO data) {
        Patient patient = patientRepository.findById(data.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        Admission episode = episodeMapper.toEntity(data);
        episode.setPatient(patient);

        Admission saved = episodeRepository.save(episode);
        // Flush so the generated id is available: care-team membership references it.
        episodeRepository.flush();
        // The creator becomes the episode's owner. This replaces the free-text
        // created_by email column as the source of ownership: that column cannot
        // express a colleague's access and is blank for records written outside an
        // authenticated request, which the old check read as "everyone may access".
        actorProvider.tryResolve()
                .ifPresent(creator -> careTeamService.enrollOwner(saved, creator.userId()));
        episodeGraph.loadPatient(saved);
        return saved;
    }

    @Override
    @Transactional
    public Admission update(Long id, AdmissionRequestDTO data) {
        Admission episode = episodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));

        if (data.getPatientId() != null && !data.getPatientId().equals(episode.getPatient().getId())) {
            Patient patient = patientRepository.findById(data.getPatientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
            episode.setPatient(patient);
        }

        episodeMapper.update(data, episode);
        Admission saved = episodeRepository.save(episode);
        episodeGraph.loadPatient(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Admission getById(Long id) {
        Admission episode = episodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        episodeGraph.loadPatient(episode);
        return episode;
    }

    @Override
    public void delete(Long id) {
        if (!episodeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Episode not found");
        }
        episodeRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getAll(Specification<Admission> spec, Pageable pageable) {
        Page<Admission> page = episodeRepository.findAll(spec, pageable);
        return buildPaginationResult(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getByPatient(Long patientId, Pageable pageable) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        Page<Admission> page = episodeRepository.findByPatientId(patientId, pageable);
        return buildPaginationResult(page);
    }

    private PaginationResultDTO buildPaginationResult(Page<Admission> page) {
        page.getContent().forEach(episodeGraph::loadPatient);
        return PaginationResultDTO.of(page);
    }
}
