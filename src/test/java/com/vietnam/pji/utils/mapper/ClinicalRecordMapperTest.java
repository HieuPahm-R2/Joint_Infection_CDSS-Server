package com.vietnam.pji.utils.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.vietnam.pji.constant.ImplantType;
import com.vietnam.pji.constant.OnsetTiming;
import com.vietnam.pji.constant.SuspectedTransmissionRoute;
import com.vietnam.pji.dto.request.ClinicalRecordRequestDTO;
import com.vietnam.pji.model.medical.ClinicalRecord;

class ClinicalRecordMapperTest {

    private final ClinicalRecordMapper mapper = Mappers.getMapper(ClinicalRecordMapper.class);

    @Test
    void toEntityTreatsBlankEnumFieldsAsNull() {
        ClinicalRecordRequestDTO dto = new ClinicalRecordRequestDTO();
        dto.setImplantStability("");
        dto.setOnsetTiming("   ");
        dto.setSuspectedTransmissionRoute("");

        ClinicalRecord record = mapper.toEntity(dto);

        assertNull(record.getImplantStability());
        assertNull(record.getOnsetTiming());
        assertNull(record.getSuspectedTransmissionRoute());
    }

    @Test
    void updateIgnoresBlankEnumFields() {
        ClinicalRecordRequestDTO dto = new ClinicalRecordRequestDTO();
        dto.setImplantStability("");
        dto.setOnsetTiming(" ");
        dto.setSuspectedTransmissionRoute(" ");

        ClinicalRecord record = new ClinicalRecord();
        record.setImplantStability(ImplantType.STABLE);
        record.setOnsetTiming(OnsetTiming.LATE);
        record.setSuspectedTransmissionRoute(SuspectedTransmissionRoute.HEMATOGENOUS);

        mapper.update(dto, record);

        assertEquals(ImplantType.STABLE, record.getImplantStability());
        assertEquals(OnsetTiming.LATE, record.getOnsetTiming());
        assertEquals(SuspectedTransmissionRoute.HEMATOGENOUS, record.getSuspectedTransmissionRoute());
    }

    @Test
    void toEntityNormalizesEnumCaseAndWhitespace() {
        ClinicalRecordRequestDTO dto = new ClinicalRecordRequestDTO();
        dto.setImplantStability(" loose ");
        dto.setOnsetTiming(" delayed_subacute ");
        dto.setSuspectedTransmissionRoute(" contiguous_spread ");

        ClinicalRecord record = mapper.toEntity(dto);

        assertEquals(ImplantType.LOOSE, record.getImplantStability());
        assertEquals(OnsetTiming.DELAYED_SUBACUTE, record.getOnsetTiming());
        assertEquals(SuspectedTransmissionRoute.CONTIGUOUS_SPREAD, record.getSuspectedTransmissionRoute());
    }
}
