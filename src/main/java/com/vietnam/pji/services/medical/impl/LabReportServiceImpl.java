package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.LabReportRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.LabReport;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.repository.AdmissionRepository;
import com.vietnam.pji.repository.LabReportRepository;
import com.vietnam.pji.services.episode.AdmissionGraphLoader;
import com.vietnam.pji.services.medical.LabReportService;
import com.vietnam.pji.services.medical.PendingLabTaskService;
import com.vietnam.pji.utils.mapper.LabReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LabReportServiceImpl implements LabReportService {

    private final LabReportRepository labResultRepository;
    private final AdmissionRepository episodeRepository;
    private final LabReportMapper labResultMapper;
    private final PendingLabTaskService pendingLabTaskService;
    private final AdmissionGraphLoader episodeGraph;

    @Override
    @Transactional
    public LabReport create(LabReportRequestDTO data) {
        Admission episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        LabReport labResult = labResultMapper.toEntity(data);
        labResult.setEpisode(episode);
        LabReport saved = labResultRepository.save(labResult);

        pendingLabTaskService.autoFulfillForEpisode(
                episode.getId(), saved.getId(),
                data.getHematologyTests(), data.getFluidAnalysis(), data.getBiochemicalData());

        episodeGraph.evictSnapshot(episode.getId());
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional
    public LabReport update(Long id, LabReportRequestDTO data) {
        LabReport labResult = labResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found"));
        labResultMapper.update(data, labResult);
        LabReport saved = labResultRepository.save(labResult);

        Long episodeId = labResult.getEpisode().getId();
        pendingLabTaskService.autoFulfillForEpisode(
                episodeId, saved.getId(),
                data.getHematologyTests(), data.getFluidAnalysis(), data.getBiochemicalData());

        episodeGraph.evictSnapshot(episodeId);
        episodeGraph.loadPatient(saved.getEpisode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public LabReport getById(Long id) {
        LabReport labResult = labResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found"));
        episodeGraph.loadPatient(labResult.getEpisode());
        return labResult;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        LabReport labResult = labResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found"));
        Long episodeId = labResult.getEpisode().getId();
        labResultRepository.deleteById(id);
        episodeGraph.evictSnapshot(episodeId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found");
        }
        Page<LabReport> page = labResultRepository.findByEpisodeId(episodeId, pageable);
        page.getContent().forEach(r -> episodeGraph.loadPatient(r.getEpisode()));
        return PaginationResultDTO.of(page);
    }
}
