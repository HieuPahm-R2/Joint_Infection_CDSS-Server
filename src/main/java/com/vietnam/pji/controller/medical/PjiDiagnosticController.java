package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.request.PjiDiagnosticEvaluationRequestDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/pji-diagnostics")
@RequiredArgsConstructor
@Tag(name = "PJI Diagnostics", description = "Deterministic PJI diagnostic evaluation")
public class PjiDiagnosticController {

    private final PjiDiagnosticRuleEngine diagnosticRuleEngine;

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate a stateless PJI diagnostic questionnaire")
    public ResponseData<PjiDiagnosticRuleEngine.DiagnosticResult> evaluate(
            @Valid @RequestBody PjiDiagnosticEvaluationRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "PJI diagnostic evaluated successfully",
                diagnosticRuleEngine.evaluate(request));
    }
}
