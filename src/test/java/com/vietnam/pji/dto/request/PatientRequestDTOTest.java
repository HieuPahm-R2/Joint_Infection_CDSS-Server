package com.vietnam.pji.dto.request;

import com.vietnam.pji.constant.GenderEnum;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidIdentityCardAndInsuranceNumberFormats() {
        assertTrue(validator.validate(request("001234567890", "123456789012345")).isEmpty());
        assertTrue(validator.validate(request("001234567890", "AB12345678")).isEmpty());
    }

    @Test
    void rejectsInvalidIdentityCardAndInsuranceNumberFormats() {
        PatientRequestDTO request = request("00123456789A", "12345678901234");

        assertTrue(validator.validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("identityCard")));
        assertTrue(validator.validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("insuranceNumber")));
    }

    private PatientRequestDTO request(String identityCard, String insuranceNumber) {
        PatientRequestDTO request = new PatientRequestDTO();
        request.setFullName("Nguyen Van A");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setGender(GenderEnum.MALE);
        request.setIdentityCard(identityCard);
        request.setInsuranceNumber(insuranceNumber);
        return request;
    }
}
