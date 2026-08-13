package com.vietnam.pji.model.medical;

import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PjiEpisodeMedicalRecordCodeTest {

    @Test
    void generatesCompactCodeWithEpisodePrefixAndCurrentYear() {
        PjiEpisode episode = new PjiEpisode();

        episode.generateMedicalRecordCode();

        String year = String.valueOf(Year.now().getValue()).substring(2);
        assertThat(episode.getMedicalRecordCode())
                .hasSize(14)
                .startsWith("BA" + year)
                .matches("BA\\d{2}[0-9A-F]{10}");
    }

    @Test
    void preservesExistingCode() {
        PjiEpisode episode = new PjiEpisode();
        episode.setMedicalRecordCode("BA000000000001");

        episode.generateMedicalRecordCode();

        assertThat(episode.getMedicalRecordCode()).isEqualTo("BA000000000001");
    }

    @Test
    void generatedSampleContainsNoDuplicates() {
        Set<String> codes = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            PjiEpisode episode = new PjiEpisode();
            episode.generateMedicalRecordCode();
            codes.add(episode.getMedicalRecordCode());
        }

        assertThat(codes).hasSize(1_000);
    }
}
