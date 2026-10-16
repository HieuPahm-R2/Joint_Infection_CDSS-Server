package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.request.AdmissionRequestDTO;
import com.vietnam.pji.model.medical.Admission;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface AdmissionMapper extends EntityMapper<AdmissionRequestDTO, Admission> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "patient", ignore = true)
    Admission toEntity(AdmissionRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "patient", ignore = true)
    void update(AdmissionRequestDTO dto, @MappingTarget Admission entity);
}
