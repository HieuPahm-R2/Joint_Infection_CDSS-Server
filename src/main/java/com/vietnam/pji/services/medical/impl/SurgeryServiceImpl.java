package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.SurgeryRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.model.medical.Surgery;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.SurgeryRepository;
import com.vietnam.pji.services.episode.EpisodeGraphLoader;
import com.vietnam.pji.services.medical.SurgeryService;
import com.vietnam.pji.utils.mapper.SurgeryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurgeryServiceImpl implements SurgeryService {

    private final SurgeryRepository surgeryRepository;
    private final EpisodeRepository episodeRepository;
    private final SurgeryMapper surgeryMapper;
    private final EpisodeGraphLoader episodeGraph;

    @Override
    @Transactional
    public Surgery create(SurgeryRequestDTO data) {
        PjiEpisode episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        Surgery surgery = surgeryMapper.toEntity(data);
        surgery.setEpisode(episode);
        Surgery saved = surgeryRepository.save(surgery);
        episodeGraph.evictSnapshot(episode.getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional
    public Surgery update(Long id, SurgeryRequestDTO data) {
        Surgery surgery = surgeryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surgery not found"));
        surgeryMapper.update(data, surgery);
        Surgery saved = surgeryRepository.save(surgery);
        episodeGraph.evictSnapshot(surgery.getEpisode().getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Surgery getById(Long id) {
        Surgery surgery = surgeryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surgery not found"));
        episodeGraph.loadPatient(surgery.getEpisode());
        return surgery;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Surgery surgery = surgeryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surgery not found"));
        Long episodeId = surgery.getEpisode().getId();
        surgeryRepository.deleteById(id);
        episodeGraph.evictSnapshot(episodeId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found");
        }
        Page<Surgery> page = surgeryRepository.findByEpisodeId(episodeId, pageable);
        page.getContent().forEach(s -> episodeGraph.loadPatient(s.getEpisode()));
        return PaginationResultDTO.of(page);
    }
}
