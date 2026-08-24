package com.vietnam.pji.constant;

import java.util.Set;

public enum RecommendationScope {
    SURGERY,
    ANTIBIOTIC,
    LEGACY_COMBINED;

    public boolean supportsDoctorDecision() {
        return this == SURGERY || this == LEGACY_COMBINED;
    }

    public boolean supportsPharmacistDecision() {
        return this == ANTIBIOTIC || this == LEGACY_COMBINED;
    }

    public Set<ItemCategory> requiredItemCategories() {
        return switch (this) {
            case SURGERY -> Set.of(ItemCategory.SURGERY_PROCEDURE);
            case ANTIBIOTIC -> Set.of(
                    ItemCategory.SYSTEMIC_ANTIBIOTIC,
                    ItemCategory.LOCAL_ANTIBIOTIC,
                    ItemCategory.ANTIBIOTIC_CARE_PLAN);
            case LEGACY_COMBINED -> Set.of(
                    ItemCategory.SYSTEMIC_ANTIBIOTIC,
                    ItemCategory.LOCAL_ANTIBIOTIC,
                    ItemCategory.SURGERY_PROCEDURE);
        };
    }
}
