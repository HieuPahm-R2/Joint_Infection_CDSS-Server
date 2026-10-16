package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.request.LabReportRequestDTO;
import com.vietnam.pji.model.medical.LabReport;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface LabReportMapper extends EntityMapper<LabReportRequestDTO, LabReport> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    LabReport toEntity(LabReportRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    void update(LabReportRequestDTO dto, @MappingTarget LabReport entity);
}
