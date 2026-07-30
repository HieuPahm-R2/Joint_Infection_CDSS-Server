package com.vietnam.pji.utils.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.dto.request.ClinicalRecordRequestDTO;
import com.vietnam.pji.model.medical.ClinicalRecord;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalRecordSurgicalDiseaseMapperTest {

    private final ClinicalRecordMapper mapper = Mappers.getMapper(ClinicalRecordMapper.class);

    @Test
    void mapsSurgicalDiseaseAndKeepsSnapshotGetterOutOfPublicJson() throws Exception {
        ClinicalRecordRequestDTO dto = new ClinicalRecordRequestDTO();
        dto.setSurgicalDisease("Theo dõi lỏng khớp háng nhân tạo");

        ClinicalRecord record = mapper.toEntity(dto);
        String json = new ObjectMapper().writeValueAsString(record);

        assertThat(record.getSurgicalDisease()).isEqualTo("Theo dõi lỏng khớp háng nhân tạo");
        assertThat(record.getNotations()).isEqualTo(record.getSurgicalDisease());
        assertThat(json).contains("\"surgicalDisease\":\"Theo dõi lỏng khớp háng nhân tạo\"");
        assertThat(json).doesNotContain("\"notations\"");
    }
}
