package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.request.SpecimenCultureRequestDTO;
import com.vietnam.pji.model.medical.SpecimenCulture;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface SpecimenCultureMapper extends EntityMapper<SpecimenCultureRequestDTO, SpecimenCulture> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    SpecimenCulture toEntity(SpecimenCultureRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    void update(SpecimenCultureRequestDTO dto, @MappingTarget SpecimenCulture entity);
}
