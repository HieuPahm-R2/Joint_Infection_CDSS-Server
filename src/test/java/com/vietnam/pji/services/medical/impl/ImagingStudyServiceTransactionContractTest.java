package com.vietnam.pji.services.medical.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.vietnam.pji.dto.request.ImagingStudyRequestDTO;

/**
 * Pins the transaction boundary of the image-result writes.
 *
 * <p>
 * Every sibling episode-scoped writer
 * ({@code ClinicalAssessmentServiceImpl}, {@code LabReportServiceImpl},
 * {@code SpecimenCultureServiceImpl}, {@code SurgeryServiceImpl}) declares
 * {@code @Transactional} on {@code create}/{@code update}, so the row write and
 * the episode snapshot-cache eviction commit together. Image results were the
 * one service that only annotated {@code delete}, so a failed save could still
 * leave the cache evicted, or a committed save could leave a stale cache entry.
 */
class ImagingStudyServiceTransactionContractTest {

    @Test
    void createAndUpdateAreTransactional() throws NoSuchMethodException {
        assertTransactional("create", ImagingStudyRequestDTO.class);
        assertTransactional("update", Long.class, ImagingStudyRequestDTO.class);
    }

    private void assertTransactional(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ImagingStudyServiceImpl.class.getMethod(name, parameterTypes);
        Transactional transaction = method.getAnnotation(Transactional.class);

        assertThat(transaction)
                .as("%s.%s must write the row and evict the snapshot cache in one transaction",
                        ImagingStudyServiceImpl.class.getSimpleName(), name)
                .isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }
}
