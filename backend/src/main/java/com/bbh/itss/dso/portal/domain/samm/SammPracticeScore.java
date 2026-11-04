package com.bbh.itss.dso.portal.domain.samm;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record SammPracticeScore(
        SammPractice practice,
        String practiceName,
        String practiceTitle,
        String practiceCode,
        SammBusinessFunction businessFunction,
        double score,
        int maturityLevel,
        List<String> criteriaMet,
        List<String> recommendations
) {
    public SammPracticeScore {
        requireNonNull(practice, "practice must not be null");
        requireNonNull(businessFunction, "businessFunction must not be null");
        criteriaMet = List.copyOf(criteriaMet != null ? criteriaMet : List.of());
        recommendations = List.copyOf(recommendations != null ? recommendations : List.of());
    }
}
