package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.request.SurgeryRequestDTO;
import com.vietnam.pji.model.medical.Surgery;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface SurgeryMapper extends EntityMapper<SurgeryRequestDTO, Surgery> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    Surgery toEntity(SurgeryRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    @Mapping(target = "positiveHistology",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "intraoperativePurulence",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void update(SurgeryRequestDTO dto, @MappingTarget Surgery entity);
}
