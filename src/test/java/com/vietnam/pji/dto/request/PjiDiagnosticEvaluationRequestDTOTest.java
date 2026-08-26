package com.vietnam.pji.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PjiDiagnosticEvaluationRequestDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void deserializesFrontendCamelCaseContract() throws Exception {
        PjiDiagnosticEvaluationRequestDTO request = objectMapper.readValue("""
                {
                  "previousArthroplasty": true,
                  "culturesPerformed": true,
                  "cultureResult": "singlePositive",
                  "daysSinceArthroplasty": 120,
                  "serumTests": {"crp": 11.0, "esr": 20.0, "dDimer": 400.0},
                  "synovialTests": {"wbc": 3200.0, "pmn": 81.0},
                  "leukocyteEsterase": "negative",
                  "alphaDefensin": "notDone",
                  "histology": "positive",
                  "purulence": "negative"
                }
                """, PjiDiagnosticEvaluationRequestDTO.class);

        assertEquals("singlePositive", request.cultureResult());
        assertEquals(11.0, request.serumTests().crp());
        assertEquals(81.0, request.synovialTests().pmn());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsUnknownEnumsAndOutOfRangePmn() {
        PjiDiagnosticEvaluationRequestDTO request = new PjiDiagnosticEvaluationRequestDTO(
                true, false, true, "ambiguous", 120, null,
                new PjiDiagnosticEvaluationRequestDTO.SynovialTests(1000.0, 101.0),
                "invalid", null, null, null);

        assertEquals(3, validator.validate(request).size());
    }
}
