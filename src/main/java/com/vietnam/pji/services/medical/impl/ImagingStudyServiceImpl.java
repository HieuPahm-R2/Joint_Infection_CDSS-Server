package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.ImagingStudyRequestDTO;
import com.vietnam.pji.dto.response.ImagingStudyResponseDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.ImagingStudy;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.repository.AdmissionRepository;
import com.vietnam.pji.repository.ImagingStudyRepository;
import com.vietnam.pji.services.episode.AdmissionGraphLoader;
import com.vietnam.pji.services.ocr.ImagingStudyService;
import com.vietnam.pji.utils.MinioChannel;
import com.vietnam.pji.utils.mapper.ImagingStudyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImagingStudyServiceImpl implements ImagingStudyService {

    private final ImagingStudyRepository imageResultRepository;
    private final AdmissionRepository episodeRepository;
    private final ImagingStudyMapper imageResultMapper;
    private final AdmissionGraphLoader episodeGraph;
    private final MinioChannel minioChannel;

    @Override
    @Transactional
    public ImagingStudyResponseDTO create(ImagingStudyRequestDTO data) {
        Admission episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        ImagingStudy imageResult = imageResultMapper.toEntity(data);
        imageResult.setEpisode(episode);
        ImagingStudy saved = imageResultRepository.save(imageResult);
        episodeGraph.evictSnapshot(episode.getId());
        return toResponseWithFreshUrl(saved);
    }

    @Override
    @Transactional
    public ImagingStudyResponseDTO update(Long id, ImagingStudyRequestDTO data) {
        ImagingStudy imageResult = imageResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image result not found"));
        imageResultMapper.update(data, imageResult);
        ImagingStudy saved = imageResultRepository.save(imageResult);
        episodeGraph.evictSnapshot(imageResult.getEpisode().getId());
        return toResponseWithFreshUrl(saved);
    }

    @Override
    public ImagingStudyResponseDTO getById(Long id) {
        ImagingStudy entity = imageResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image result not found"));
        return toResponseWithFreshUrl(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ImagingStudy imageResult = imageResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image result not found"));
        Long episodeId = imageResult.getEpisode().getId();
        imageResultRepository.deleteById(id);
        episodeGraph.evictSnapshot(episodeId);
    }

    @Override
    public PaginationResultDTO getByEpisode(Long episodeId, Pageable pageable) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found");
        }
        Page<ImagingStudy> page = imageResultRepository.findByEpisodeId(episodeId, pageable);
        List<ImagingStudyResponseDTO> items = page.getContent().stream()
                .map(this::toResponseWithFreshUrl)
                .toList();
        return PaginationResultDTO.of(page, items);
    }

    /**
     * Map entity to response DTO and stamp a fresh presigned URL.
     * Falls back to null URL when bucket/objectKey aren't set yet (legacy rows).
     */
    private ImagingStudyResponseDTO toResponseWithFreshUrl(ImagingStudy entity) {
        ImagingStudyResponseDTO dto = imageResultMapper.toResponse(entity);
        if (entity.getBucket() != null && entity.getObjectKey() != null) {
            dto.setUrl(minioChannel.presignedGetUrl(entity.getBucket(), entity.getObjectKey()));
        }
        return dto;
    }
}
