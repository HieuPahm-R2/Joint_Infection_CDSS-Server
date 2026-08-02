package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.request.EpisodeRequestDTO;
import com.vietnam.pji.model.medical.EpisodeDepartmentTransfer;
import com.vietnam.pji.model.medical.PjiEpisode;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EpisodeMedicalRecordMapperTest {

    private final EpisodeMapper mapper = Mappers.getMapper(EpisodeMapper.class);

    @Test
    void mapsRenewedEpisodeManagementAndDiagnosisFields() {
        EpisodeDepartmentTransfer transfer = EpisodeDepartmentTransfer.builder()
                .department("B2")
                .admissionDate(LocalDate.of(2026, 6, 30))
                .admissionTime(LocalTime.of(9, 15))
                .treatmentDays(2)
                .build();

        EpisodeRequestDTO dto = new EpisodeRequestDTO();
        dto.setAdmissionDate(LocalDate.of(2026, 6, 29));
        dto.setInitialDepartmentAdmissionDate(LocalDate.of(2026, 6, 29));
        dto.setInitialDepartmentTreatmentDays(1);
        dto.setDepartmentTransfers(List.of(transfer));
        dto.setAdmissionCount(2);
        dto.setHospitalTransferType("TRANSFER_OUT");
        dto.setHospitalTransferDestination("Bệnh viện tuyến trên");
        dto.setDischargeDisposition("DISCHARGED");
        dto.setInpatientDiagnosis("Đau khớp");
        dto.setHasComplication(true);
        dto.setComplicationCause("INFECTION");
        dto.setPostoperativeTreatmentDays(7);
        dto.setSurgeryCount(1);
        dto.setDischargePrimaryDiagnosis("Theo dõi lỏng khớp háng");

        PjiEpisode entity = mapper.toEntity(dto);

        assertThat(entity.getInitialDepartmentTreatmentDays()).isEqualTo(1);
        assertThat(entity.getDepartmentTransfers()).singleElement().satisfies(mapped -> {
            assertThat(mapped.getDepartment()).isEqualTo("B2");
            assertThat(mapped.getTreatmentDays()).isEqualTo(2);
        });
        assertThat(entity.getAdmissionCount()).isEqualTo(2);
        assertThat(entity.getHospitalTransferDestination()).isEqualTo("Bệnh viện tuyến trên");
        assertThat(entity.getInpatientDiagnosis()).isEqualTo("Đau khớp");
        assertThat(entity.getHasComplication()).isTrue();
        assertThat(entity.getDischargePrimaryDiagnosis()).isEqualTo("Theo dõi lỏng khớp háng");
    }
}
