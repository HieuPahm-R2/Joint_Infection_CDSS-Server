package com.vietnam.pji.repository;

import com.vietnam.pji.model.medical.SensitivityResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface SensitivityResultRepository extends JpaRepository<SensitivityResult, Long>, JpaSpecificationExecutor<SensitivityResult> {
    Page<SensitivityResult> findByCultureId(Long cultureId, Pageable pageable);

    List<SensitivityResult> findByCultureId(Long cultureId);

    List<SensitivityResult> findByCultureIdIn(Collection<Long> cultureIds);
}
