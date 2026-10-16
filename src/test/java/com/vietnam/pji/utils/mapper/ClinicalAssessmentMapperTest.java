package com.vietnam.pji.utils.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.vietnam.pji.constant.ImplantStability;
import com.vietnam.pji.constant.OnsetTiming;
import com.vietnam.pji.constant.SuspectedTransmissionRoute;
import com.vietnam.pji.dto.request.ClinicalAssessmentRequestDTO;
import com.vietnam.pji.model.medical.ClinicalAssessment;

class ClinicalAssessmentMapperTest {

    private final ClinicalAssessmentMapper mapper = Mappers.getMapper(ClinicalAssessmentMapper.class);

    @Test
    void toEntityTreatsBlankEnumFieldsAsNull() {
        ClinicalAssessmentRequestDTO dto = new ClinicalAssessmentRequestDTO();
        dto.setImplantStability("");
        dto.setOnsetTiming("   ");
        dto.setSuspectedTransmissionRoute("");

        ClinicalAssessment record = mapper.toEntity(dto);

        assertNull(record.getImplantStability());
        assertNull(record.getOnsetTiming());
        assertNull(record.getSuspectedTransmissionRoute());
    }

    @Test
    void updateIgnoresBlankEnumFields() {
        ClinicalAssessmentRequestDTO dto = new ClinicalAssessmentRequestDTO();
        dto.setImplantStability("");
        dto.setOnsetTiming(" ");
        dto.setSuspectedTransmissionRoute(" ");

        ClinicalAssessment record = new ClinicalAssessment();
        record.setImplantStability(ImplantStability.STABLE);
        record.setOnsetTiming(OnsetTiming.LATE);
        record.setSuspectedTransmissionRoute(SuspectedTransmissionRoute.HEMATOGENOUS);

        mapper.update(dto, record);

        assertEquals(ImplantStability.STABLE, record.getImplantStability());
        assertEquals(OnsetTiming.LATE, record.getOnsetTiming());
        assertEquals(SuspectedTransmissionRoute.HEMATOGENOUS, record.getSuspectedTransmissionRoute());
    }

    @Test
    void toEntityNormalizesEnumCaseAndWhitespace() {
        ClinicalAssessmentRequestDTO dto = new ClinicalAssessmentRequestDTO();
        dto.setImplantStability(" loose ");
        dto.setOnsetTiming(" delayed_subacute ");
        dto.setSuspectedTransmissionRoute(" contiguous_spread ");

        ClinicalAssessment record = mapper.toEntity(dto);

        assertEquals(ImplantStability.LOOSE, record.getImplantStability());
        assertEquals(OnsetTiming.DELAYED_SUBACUTE, record.getOnsetTiming());
        assertEquals(SuspectedTransmissionRoute.CONTIGUOUS_SPREAD, record.getSuspectedTransmissionRoute());
    }
}
