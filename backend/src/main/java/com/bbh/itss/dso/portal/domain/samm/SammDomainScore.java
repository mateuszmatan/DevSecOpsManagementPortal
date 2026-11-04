package com.bbh.itss.dso.portal.domain.samm;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record SammDomainScore(
        SammBusinessFunction function,
        String title,
        String description,
        double score,
        int maturityLevel,
        List<SammPracticeScore> practices
) {
    public SammDomainScore {
        requireNonNull(function, "function must not be null");
        practices = List.copyOf(practices != null ? practices : List.of());
    }
}
