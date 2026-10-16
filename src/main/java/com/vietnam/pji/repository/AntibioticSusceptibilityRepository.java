package com.vietnam.pji.repository;

import com.vietnam.pji.model.medical.AntibioticSusceptibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface AntibioticSusceptibilityRepository extends JpaRepository<AntibioticSusceptibility, Long>, JpaSpecificationExecutor<AntibioticSusceptibility> {
    Page<AntibioticSusceptibility> findByCultureId(Long cultureId, Pageable pageable);

    List<AntibioticSusceptibility> findByCultureId(Long cultureId);

    List<AntibioticSusceptibility> findByCultureIdIn(Collection<Long> cultureIds);
}
