package com.vietnam.pji.utils.mapper;

import java.util.Locale;

import com.vietnam.pji.constant.ImplantStability;
import com.vietnam.pji.constant.OnsetTiming;
import com.vietnam.pji.constant.SuspectedTransmissionRoute;
import com.vietnam.pji.dto.request.ClinicalAssessmentRequestDTO;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface ClinicalAssessmentMapper extends EntityMapper<ClinicalAssessmentRequestDTO, ClinicalAssessment> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    ClinicalAssessment toEntity(ClinicalAssessmentRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    void update(ClinicalAssessmentRequestDTO dto, @MappingTarget ClinicalAssessment entity);

    default ImplantStability mapImplantType(String value) {
        return parseEnum(value, ImplantStability.class, "implantStability");
    }

    default OnsetTiming mapOnsetTiming(String value) {
        return parseEnum(value, OnsetTiming.class, "onsetTiming");
    }

    default SuspectedTransmissionRoute mapSuspectedTransmissionRoute(String value) {
        return parseEnum(value, SuspectedTransmissionRoute.class, "suspectedTransmissionRoute");
    }

    @BeforeMapping
    default void normalizeEnumFields(ClinicalAssessmentRequestDTO dto) {
        if (dto == null) {
            return;
        }
        dto.setImplantStability(blankToNull(dto.getImplantStability()));
        dto.setOnsetTiming(blankToNull(dto.getOnsetTiming()));
        dto.setSuspectedTransmissionRoute(blankToNull(dto.getSuspectedTransmissionRoute()));
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidDataException("Invalid " + fieldName + ": " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
