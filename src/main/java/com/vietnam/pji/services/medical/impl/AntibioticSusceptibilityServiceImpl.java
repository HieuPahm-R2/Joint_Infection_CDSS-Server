package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.dto.request.AntibioticSusceptibilityRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.model.medical.AntibioticSusceptibility;
import com.vietnam.pji.repository.AntibioticSusceptibilityRepository;
import com.vietnam.pji.repository.medical.SpecimenCultureRepository;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.services.medical.AntibioticSusceptibilityService;
import com.vietnam.pji.utils.mapper.AntibioticSusceptibilityMapper;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AntibioticSusceptibilityServiceImpl implements AntibioticSusceptibilityService {

    private final AntibioticSusceptibilityRepository sensitivityResultRepository;
    private final SpecimenCultureRepository cultureResultRepository;
    private final AntibioticSusceptibilityMapper sensitivityResultMapper;
    private final RedisService redisService;

    @Override
    @Transactional
    public AntibioticSusceptibility create(AntibioticSusceptibilityRequestDTO data) {
        SpecimenCulture culture = cultureResultRepository.findById(data.getCultureId())
                .orElseThrow(() -> new ResourceNotFoundException("Culture result not found"));
        AntibioticSusceptibility result = sensitivityResultMapper.toEntity(data);
        result.setCulture(culture);
        AntibioticSusceptibility saved = sensitivityResultRepository.save(result);
        // Evict snapshot: sensitivity → culture → episode
        redisService.evictSnapshotCache(culture.getEpisode().getId());
        Hibernate.initialize(saved.getCulture());
        Hibernate.initialize(saved.getCulture().getEpisode());
        Hibernate.initialize(saved.getCulture().getEpisode().getPatient());
        return saved;
    }

    @Override
    @Transactional
    public AntibioticSusceptibility update(Long id, AntibioticSusceptibilityRequestDTO data) {
        AntibioticSusceptibility result = sensitivityResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sensitivity result not found"));
        sensitivityResultMapper.update(data, result);
        AntibioticSusceptibility saved = sensitivityResultRepository.save(result);
        // Navigate: sensitivity → culture → episode
        SpecimenCulture culture = cultureResultRepository.findById(result.getCulture().getId())
                .orElse(null);
        if (culture != null) {
            redisService.evictSnapshotCache(culture.getEpisode().getId());
        }
        Hibernate.initialize(saved.getCulture());
        Hibernate.initialize(saved.getCulture().getEpisode());
        Hibernate.initialize(saved.getCulture().getEpisode().getPatient());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AntibioticSusceptibility getById(Long id) {
        AntibioticSusceptibility result = sensitivityResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sensitivity result not found"));
        Hibernate.initialize(result.getCulture());
        Hibernate.initialize(result.getCulture().getEpisode());
        Hibernate.initialize(result.getCulture().getEpisode().getPatient());
        return result;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AntibioticSusceptibility result = sensitivityResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sensitivity result not found"));
        // Get episodeId before deleting
        SpecimenCulture culture = cultureResultRepository.findById(result.getCulture().getId())
                .orElse(null);
        Long episodeId = (culture != null) ? culture.getEpisode().getId() : null;
        sensitivityResultRepository.deleteById(id);
        if (episodeId != null) {
            redisService.evictSnapshotCache(episodeId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getByCulture(Long cultureId, Pageable pageable) {
        if (!cultureResultRepository.existsById(cultureId)) {
            throw new ResourceNotFoundException("Culture result not found");
        }
        Page<AntibioticSusceptibility> page = sensitivityResultRepository.findByCultureId(cultureId, pageable);
        page.getContent().forEach(r -> {
            Hibernate.initialize(r.getCulture());
            Hibernate.initialize(r.getCulture().getEpisode());
            Hibernate.initialize(r.getCulture().getEpisode().getPatient());
        });
        return PaginationResultDTO.of(page);
    }
}
