package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.request.AntibioticSusceptibilityRequestDTO;
import com.vietnam.pji.model.medical.AntibioticSusceptibility;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface AntibioticSusceptibilityMapper extends EntityMapper<AntibioticSusceptibilityRequestDTO, AntibioticSusceptibility> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "culture", ignore = true)
    AntibioticSusceptibility toEntity(AntibioticSusceptibilityRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "culture", ignore = true)
    void update(AntibioticSusceptibilityRequestDTO dto, @MappingTarget AntibioticSusceptibility entity);
}
