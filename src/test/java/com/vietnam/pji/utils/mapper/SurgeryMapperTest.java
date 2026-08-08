package com.vietnam.pji.utils.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.vietnam.pji.dto.request.SurgeryRequestDTO;
import com.vietnam.pji.model.medical.Surgery;

class SurgeryMapperTest {

    private final SurgeryMapper mapper = Mappers.getMapper(SurgeryMapper.class);

    @Test
    void mapsStructuredIcmEvidenceOnCreate() {
        SurgeryRequestDTO dto = new SurgeryRequestDTO();
        dto.setPositiveHistology(true);
        dto.setIntraoperativePurulence(false);

        Surgery surgery = mapper.toEntity(dto);

        assertTrue(surgery.getPositiveHistology());
        assertFalse(surgery.getIntraoperativePurulence());
    }

    @Test
    void updateCanClearStructuredIcmEvidenceBackToUnknown() {
        SurgeryRequestDTO dto = new SurgeryRequestDTO();
        Surgery surgery = Surgery.builder()
                .positiveHistology(true)
                .intraoperativePurulence(false)
                .build();

        mapper.update(dto, surgery);

        assertNull(surgery.getPositiveHistology());
        assertNull(surgery.getIntraoperativePurulence());
    }
}
