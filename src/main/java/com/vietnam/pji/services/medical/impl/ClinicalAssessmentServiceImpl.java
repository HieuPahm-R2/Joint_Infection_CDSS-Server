package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.ClinicalAssessmentRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.repository.AdmissionRepository;
import com.vietnam.pji.repository.medical.ClinicalAssessmentRepository;
import com.vietnam.pji.services.episode.AdmissionGraphLoader;
import com.vietnam.pji.services.medical.ClinicalAssessmentService;
import com.vietnam.pji.utils.mapper.ClinicalAssessmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClinicalAssessmentServiceImpl implements ClinicalAssessmentService {

    private final ClinicalAssessmentRepository clinicalRecordRepository;
    private final AdmissionRepository episodeRepository;
    private final ClinicalAssessmentMapper clinicalRecordMapper;
    private final AdmissionGraphLoader episodeGraph;

    @Override
    @Transactional
    public ClinicalAssessment create(ClinicalAssessmentRequestDTO data) {
        Admission episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        ClinicalAssessment record = clinicalRecordMapper.toEntity(data);
        record.setEpisode(episode);
        ClinicalAssessment saved = clinicalRecordRepository.save(record);
        episodeGraph.evictSnapshot(episode.getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional
    public ClinicalAssessment update(Long id, ClinicalAssessmentRequestDTO data) {
        ClinicalAssessment record = clinicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical record not found"));
        clinicalRecordMapper.update(data, record);
        ClinicalAssessment saved = clinicalRecordRepository.save(record);
        episodeGraph.evictSnapshot(record.getEpisode().getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalAssessment getById(Long id) {
        ClinicalAssessment record = clinicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical record not found"));
        episodeGraph.loadPatient(record.getEpisode());
        return record;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ClinicalAssessment record = clinicalRecordRepository.findById(id)
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
        Page<ClinicalAssessment> page = clinicalRecordRepository.findByEpisodeId(episodeId, pageable);
        page.getContent().forEach(r -> episodeGraph.loadPatient(r.getEpisode()));
        return PaginationResultDTO.of(page);
    }
}
