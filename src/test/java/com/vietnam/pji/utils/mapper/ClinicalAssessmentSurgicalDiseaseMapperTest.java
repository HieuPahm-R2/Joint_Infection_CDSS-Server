package com.vietnam.pji.utils.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.dto.request.ClinicalAssessmentRequestDTO;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalAssessmentSurgicalDiseaseMapperTest {

    private final ClinicalAssessmentMapper mapper = Mappers.getMapper(ClinicalAssessmentMapper.class);

    @Test
    void mapsGeneralExamAndSurgicalDiseaseAndKeepsSnapshotGetterOutOfPublicJson() throws Exception {
        ClinicalAssessmentRequestDTO dto = new ClinicalAssessmentRequestDTO();
        dto.setGeneralExam("Tỉnh táo, dấu hiệu sinh tồn ổn định");
        dto.setSurgicalDisease("Theo dõi lỏng khớp háng nhân tạo");

        ClinicalAssessment record = mapper.toEntity(dto);
        String json = new ObjectMapper().writeValueAsString(record);

        assertThat(record.getGeneralExam()).isEqualTo("Tỉnh táo, dấu hiệu sinh tồn ổn định");
        assertThat(record.getSurgicalDisease()).isEqualTo("Theo dõi lỏng khớp háng nhân tạo");
        assertThat(record.getNotations()).isEqualTo(record.getSurgicalDisease());
        assertThat(json).contains("\"generalExam\":\"Tỉnh táo, dấu hiệu sinh tồn ổn định\"");
        assertThat(json).contains("\"surgicalDisease\":\"Theo dõi lỏng khớp háng nhân tạo\"");
        assertThat(json).doesNotContain("\"notations\"");
    }
}
