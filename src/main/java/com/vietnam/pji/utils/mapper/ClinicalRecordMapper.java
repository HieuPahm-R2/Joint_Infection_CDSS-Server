package com.vietnam.pji.utils.mapper;

import java.util.Locale;

import com.vietnam.pji.constant.ImplantType;
import com.vietnam.pji.constant.OnsetTiming;
import com.vietnam.pji.constant.SuspectedTransmissionRoute;
import com.vietnam.pji.dto.request.ClinicalRecordRequestDTO;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.medical.ClinicalRecord;
import org.mapstruct.*;

@Mapper(config = DefaultConfigMapper.class)
public interface ClinicalRecordMapper extends EntityMapper<ClinicalRecordRequestDTO, ClinicalRecord> {

    @Override
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    ClinicalRecord toEntity(ClinicalRecordRequestDTO dto);

    @Override
    @Named("update")
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "episode", ignore = true)
    void update(ClinicalRecordRequestDTO dto, @MappingTarget ClinicalRecord entity);

    default ImplantType mapImplantType(String value) {
        return parseEnum(value, ImplantType.class, "implantStability");
    }

    default OnsetTiming mapOnsetTiming(String value) {
        return parseEnum(value, OnsetTiming.class, "onsetTiming");
    }

    default SuspectedTransmissionRoute mapSuspectedTransmissionRoute(String value) {
        return parseEnum(value, SuspectedTransmissionRoute.class, "suspectedTransmissionRoute");
    }

    @BeforeMapping
    default void normalizeEnumFields(ClinicalRecordRequestDTO dto) {
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
