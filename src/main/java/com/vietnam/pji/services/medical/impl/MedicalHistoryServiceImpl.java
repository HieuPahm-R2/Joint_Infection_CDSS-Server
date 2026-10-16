package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.MedicalHistoryRequestDTO;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.MedicalHistory;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.MedicalHistoryRepository;
import com.vietnam.pji.services.episode.EpisodeGraphLoader;
import com.vietnam.pji.services.medical.MedicalHistoryService;
import com.vietnam.pji.utils.mapper.MedicalHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicalHistoryServiceImpl implements MedicalHistoryService {

    private final MedicalHistoryRepository medicalHistoryRepository;
    private final EpisodeRepository episodeRepository;
    private final MedicalHistoryMapper medicalHistoryMapper;
    private final EpisodeGraphLoader episodeGraph;

    @Override
    @Transactional
    public MedicalHistory create(Long episodeId, MedicalHistoryRequestDTO data) {
        if (medicalHistoryRepository.existsByEpisodeId(episodeId)) {
            throw new InvalidDataException("Medical history already exists for this episode.");
        }
        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));

        MedicalHistory history = medicalHistoryMapper.toEntity(data);
        history.setEpisode(episode);
        MedicalHistory saved = medicalHistoryRepository.save(history);
        episodeGraph.evictSnapshot(episodeId);
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional
    public MedicalHistory update(Long episodeId, MedicalHistoryRequestDTO data) {
        MedicalHistory history = medicalHistoryRepository.findByEpisodeId(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical history not found for this episode"));
        medicalHistoryMapper.update(data, history);
        MedicalHistory saved = medicalHistoryRepository.save(history);
        episodeGraph.evictSnapshot(episodeId);
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalHistory getByEpisodeId(Long episodeId) {
        MedicalHistory history = medicalHistoryRepository.findByEpisodeId(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical history not found for this episode"));
        episodeGraph.loadPatient(history.getEpisode());
        return history;
    }
}
