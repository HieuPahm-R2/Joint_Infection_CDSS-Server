package com.vietnam.pji.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;

/**
 * Stateless input for the manual PJI calculator.
 *
 * <p>Fields are intentionally nullable: an unanswered or unperformed test is
 * clinical uncertainty and must reach the rule engine as {@code unknown}, not
 * be coerced to a negative result.
 */
public record PjiDiagnosticEvaluationRequestDTO(
        Boolean previousArthroplasty,
        Boolean sinusTract,
        Boolean culturesPerformed,
        @Pattern(regexp = "negative|singlePositive|multipleSameOrganism|multipleDifferentOrganisms")
        String cultureResult,
        @PositiveOrZero Integer daysSinceArthroplasty,
        @Valid SerumTests serumTests,
        @Valid SynovialTests synovialTests,
        @Pattern(regexp = "notDone|negative|trace|onePlus|twoPlus") String leukocyteEsterase,
        @Pattern(regexp = "notDone|negative|positive") String alphaDefensin,
        @Pattern(regexp = "notDone|negative|positive") String histology,
        @Pattern(regexp = "notDone|negative|positive") String purulence) {

    public record SerumTests(
            @PositiveOrZero Double esr,
            @PositiveOrZero Double crp,
            @PositiveOrZero Double dDimer) {
    }

    public record SynovialTests(
            @PositiveOrZero Double wbc,
            @PositiveOrZero @DecimalMax("100") Double pmn) {
    }
}
