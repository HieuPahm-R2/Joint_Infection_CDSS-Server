package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.request.ImagingStudyRequestDTO;
import com.vietnam.pji.dto.response.ImagingStudyResponseDTO;
import com.vietnam.pji.model.medical.ImagingStudy;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface ImagingStudyMapper extends EntityMapper<ImagingStudyRequestDTO, ImagingStudy> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ImagingStudy toEntity(ImagingStudyRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(ImagingStudyRequestDTO dto, @MappingTarget ImagingStudy entity);

    @Mapping(target = "episodeId", source = "episode.id")
    @Mapping(target = "url", ignore = true) // populated by the service from MinioChannel
    ImagingStudyResponseDTO toResponse(ImagingStudy entity);
}
