package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.ClinicalRecordRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.ClinicalRecord;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.medical.ClinicalRecordRepository;
import com.vietnam.pji.services.episode.EpisodeGraphLoader;
import com.vietnam.pji.services.medical.ClinicalRecordService;
import com.vietnam.pji.utils.mapper.ClinicalRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClinicalRecordServiceImpl implements ClinicalRecordService {

    private final ClinicalRecordRepository clinicalRecordRepository;
    private final EpisodeRepository episodeRepository;
    private final ClinicalRecordMapper clinicalRecordMapper;
    private final EpisodeGraphLoader episodeGraph;

    @Override
    @Transactional
    public ClinicalRecord create(ClinicalRecordRequestDTO data) {
        PjiEpisode episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        ClinicalRecord record = clinicalRecordMapper.toEntity(data);
        record.setEpisode(episode);
        ClinicalRecord saved = clinicalRecordRepository.save(record);
        episodeGraph.evictSnapshot(episode.getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional
    public ClinicalRecord update(Long id, ClinicalRecordRequestDTO data) {
        ClinicalRecord record = clinicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical record not found"));
        clinicalRecordMapper.update(data, record);
        ClinicalRecord saved = clinicalRecordRepository.save(record);
        episodeGraph.evictSnapshot(record.getEpisode().getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalRecord getById(Long id) {
        ClinicalRecord record = clinicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical record not found"));
        episodeGraph.loadPatient(record.getEpisode());
        return record;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ClinicalRecord record = clinicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical record not found"));
        Long episodeId = record.getEpisode().getId();
        clinicalRecordRepository.deleteById(id);
        episodeGraph.evictSnapshot(episodeId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found");
        }
        Page<ClinicalRecord> page = clinicalRecordRepository.findByEpisodeId(episodeId, pageable);
        page.getContent().forEach(r -> episodeGraph.loadPatient(r.getEpisode()));
        return PaginationResultDTO.of(page);
    }
}
