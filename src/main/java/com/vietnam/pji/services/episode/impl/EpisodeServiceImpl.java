package com.vietnam.pji.services.episode.impl;

import com.vietnam.pji.dto.request.EpisodeRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.Patient;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.PatientRepository;
import com.vietnam.pji.services.episode.EpisodeCareTeamService;
import com.vietnam.pji.services.episode.EpisodeGraphLoader;
import com.vietnam.pji.services.episode.EpisodeService;
import com.vietnam.pji.services.auth.CurrentActorProvider;
import com.vietnam.pji.utils.mapper.EpisodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EpisodeServiceImpl implements EpisodeService {

    private final EpisodeRepository episodeRepository;
    private final PatientRepository patientRepository;
    private final EpisodeMapper episodeMapper;
    private final EpisodeGraphLoader episodeGraph;
    private final EpisodeCareTeamService careTeamService;
    private final CurrentActorProvider actorProvider;

    @Override
    @Transactional
    public PjiEpisode create(EpisodeRequestDTO data) {
        Patient patient = patientRepository.findById(data.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        PjiEpisode episode = episodeMapper.toEntity(data);
        episode.setPatient(patient);

        PjiEpisode saved = episodeRepository.save(episode);
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
    public PjiEpisode update(Long id, EpisodeRequestDTO data) {
        PjiEpisode episode = episodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));

        if (data.getPatientId() != null && !data.getPatientId().equals(episode.getPatient().getId())) {
            Patient patient = patientRepository.findById(data.getPatientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
            episode.setPatient(patient);
        }

        episodeMapper.update(data, episode);
        PjiEpisode saved = episodeRepository.save(episode);
        episodeGraph.loadPatient(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PjiEpisode getById(Long id) {
        PjiEpisode episode = episodeRepository.findById(id)
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
    public PaginationResultDTO getAll(Specification<PjiEpisode> spec, Pageable pageable) {
        Page<PjiEpisode> page = episodeRepository.findAll(spec, pageable);
        return buildPaginationResult(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getByPatient(Long patientId, Pageable pageable) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        Page<PjiEpisode> page = episodeRepository.findByPatientId(patientId, pageable);
        return buildPaginationResult(page);
    }

    private PaginationResultDTO buildPaginationResult(Page<PjiEpisode> page) {
        page.getContent().forEach(episodeGraph::loadPatient);
        return PaginationResultDTO.of(page);
    }
}
