package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.SpecimenCultureRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.repository.AdmissionRepository;
import com.vietnam.pji.repository.medical.SpecimenCultureRepository;
import com.vietnam.pji.services.episode.AdmissionGraphLoader;
import com.vietnam.pji.services.medical.SpecimenCultureService;
import com.vietnam.pji.utils.mapper.SpecimenCultureMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpecimenCultureServiceImpl implements SpecimenCultureService {

    private final SpecimenCultureRepository cultureResultRepository;
    private final AdmissionRepository episodeRepository;
    private final SpecimenCultureMapper cultureResultMapper;
    private final AdmissionGraphLoader episodeGraph;

    @Override
    @Transactional
    public SpecimenCulture create(SpecimenCultureRequestDTO data) {
        Admission episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        SpecimenCulture cultureResult = cultureResultMapper.toEntity(data);
        cultureResult.setEpisode(episode);
        SpecimenCulture saved = cultureResultRepository.save(cultureResult);
        episodeGraph.evictSnapshot(episode.getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional
    public SpecimenCulture update(Long id, SpecimenCultureRequestDTO data) {
        SpecimenCulture cultureResult = cultureResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Culture result not found"));
        cultureResultMapper.update(data, cultureResult);
        SpecimenCulture saved = cultureResultRepository.save(cultureResult);
        episodeGraph.evictSnapshot(cultureResult.getEpisode().getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public SpecimenCulture getById(Long id) {
        SpecimenCulture cultureResult = cultureResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Culture result not found"));
        episodeGraph.loadPatient(cultureResult.getEpisode());
        return cultureResult;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SpecimenCulture cultureResult = cultureResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Culture result not found"));
        Long episodeId = cultureResult.getEpisode().getId();
        cultureResultRepository.deleteById(id);
        episodeGraph.evictSnapshot(episodeId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found");
        }
        Page<SpecimenCulture> page = cultureResultRepository.findByEpisodeId(episodeId, pageable);
        page.getContent().forEach(r -> episodeGraph.loadPatient(r.getEpisode()));
        return PaginationResultDTO.of(page);
    }
}
