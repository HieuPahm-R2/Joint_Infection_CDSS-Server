package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ClinicalRecordRequestDTO {

    @NotNull(message = "episodeId must not be null")
    private Long episodeId;

    private String onsetTiming;

    private String bloodPressure;

    private BigDecimal heightCm; // chiều cao (cm)

    private BigDecimal weightKg; // cân nặng (kg)

    private BigDecimal bmi;

    private Boolean fever;
    private Boolean pain;
    private Boolean erythema; // có ban đỏ
    private Boolean swelling; // sưng tấy
    private Boolean sinusTract; // có đường rò

    private Boolean hematogenousSuspected; // nghi ngờ lây truyền qua đường máu

    private Boolean pmmaAllergy;

    private String suspectedTransmissionRoute;

    private String softTissue; // tình trạng mô mềm

    private String implantStability; // độ ổn định của cấy ghép

    private String prosthesisJoint;

    private String surgicalDisease;
}
