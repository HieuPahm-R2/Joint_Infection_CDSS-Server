package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.ImageResultRequestDTO;
import com.vietnam.pji.dto.response.ImageResultResponseDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.ImageResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ImageResultRepository;
import com.vietnam.pji.services.episode.EpisodeGraphLoader;
import com.vietnam.pji.services.ocr.ImageResultService;
import com.vietnam.pji.utils.MinioChannel;
import com.vietnam.pji.utils.mapper.ImageResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageResultServiceImpl implements ImageResultService {

    private final ImageResultRepository imageResultRepository;
    private final EpisodeRepository episodeRepository;
    private final ImageResultMapper imageResultMapper;
    private final EpisodeGraphLoader episodeGraph;
    private final MinioChannel minioChannel;

    @Override
    @Transactional
    public ImageResultResponseDTO create(ImageResultRequestDTO data) {
        PjiEpisode episode = episodeRepository.findById(data.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        ImageResult imageResult = imageResultMapper.toEntity(data);
        imageResult.setEpisode(episode);
        ImageResult saved = imageResultRepository.save(imageResult);
        episodeGraph.evictSnapshot(episode.getId());
        return toResponseWithFreshUrl(saved);
    }

    @Override
    @Transactional
    public ImageResultResponseDTO update(Long id, ImageResultRequestDTO data) {
        ImageResult imageResult = imageResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image result not found"));
        imageResultMapper.update(data, imageResult);
        ImageResult saved = imageResultRepository.save(imageResult);
        episodeGraph.evictSnapshot(imageResult.getEpisode().getId());
        return toResponseWithFreshUrl(saved);
    }

    @Override
    public ImageResultResponseDTO getById(Long id) {
        ImageResult entity = imageResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image result not found"));
        return toResponseWithFreshUrl(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ImageResult imageResult = imageResultRepository.findById(id)
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
        Page<ImageResult> page = imageResultRepository.findByEpisodeId(episodeId, pageable);
        List<ImageResultResponseDTO> items = page.getContent().stream()
                .map(this::toResponseWithFreshUrl)
                .toList();
        return PaginationResultDTO.of(page, items);
    }

    /**
     * Map entity to response DTO and stamp a fresh presigned URL.
     * Falls back to null URL when bucket/objectKey aren't set yet (legacy rows).
     */
    private ImageResultResponseDTO toResponseWithFreshUrl(ImageResult entity) {
        ImageResultResponseDTO dto = imageResultMapper.toResponse(entity);
        if (entity.getBucket() != null && entity.getObjectKey() != null) {
            dto.setUrl(minioChannel.presignedGetUrl(entity.getBucket(), entity.getObjectKey()));
        }
        return dto;
    }
}
