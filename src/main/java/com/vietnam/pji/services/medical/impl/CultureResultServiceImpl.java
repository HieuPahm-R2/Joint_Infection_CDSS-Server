package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.CultureResultRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.CultureResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.medical.CultureResultRepository;
import com.vietnam.pji.services.episode.EpisodeGraphLoader;
import com.vietnam.pji.services.medical.CultureResultService;
import com.vietnam.pji.utils.mapper.CultureResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CultureResultServiceImpl implements CultureResultService {

    private final CultureResultRepository cultureResultRepository;
    private final EpisodeRepository episodeRepository;
    private final CultureResultMapper cultureResultMapper;
    private final EpisodeGraphLoader episodeGraph;

    @Override
    @Transactional
    public CultureResult create(CultureResultRequestDTO data) {
        PjiEpisode episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        CultureResult cultureResult = cultureResultMapper.toEntity(data);
        cultureResult.setEpisode(episode);
        CultureResult saved = cultureResultRepository.save(cultureResult);
        episodeGraph.evictSnapshot(episode.getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional
    public CultureResult update(Long id, CultureResultRequestDTO data) {
        CultureResult cultureResult = cultureResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Culture result not found"));
        cultureResultMapper.update(data, cultureResult);
        CultureResult saved = cultureResultRepository.save(cultureResult);
        episodeGraph.evictSnapshot(cultureResult.getEpisode().getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public CultureResult getById(Long id) {
        CultureResult cultureResult = cultureResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Culture result not found"));
        episodeGraph.loadPatient(cultureResult.getEpisode());
        return cultureResult;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CultureResult cultureResult = cultureResultRepository.findById(id)
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
        Page<CultureResult> page = cultureResultRepository.findByEpisodeId(episodeId, pageable);
        page.getContent().forEach(r -> episodeGraph.loadPatient(r.getEpisode()));
        return PaginationResultDTO.of(page);
    }
}
