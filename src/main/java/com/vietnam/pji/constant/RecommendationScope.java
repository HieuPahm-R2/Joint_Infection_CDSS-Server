package com.vietnam.pji.constant;

import java.util.Set;

public enum RecommendationScope {
    SURGERY,
    ANTIBIOTIC;

    public boolean supportsDoctorDecision() {
        return this == SURGERY;
    }

    public boolean supportsPharmacistDecision() {
        return this == ANTIBIOTIC;
    }

    public Set<ItemCategory> requiredItemCategories() {
        return switch (this) {
            case SURGERY -> Set.of(ItemCategory.SURGERY_PROCEDURE);
            case ANTIBIOTIC -> Set.of(
                    ItemCategory.SYSTEMIC_ANTIBIOTIC,
                    ItemCategory.LOCAL_ANTIBIOTIC);
        };
    }
}
