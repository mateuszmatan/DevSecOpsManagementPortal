package com.bbh.itss.dso.portal.domain.samm;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public record SammDepartmentScore(
        Long departmentId,
        String departmentName,
        int applicationCount,
        double overallScore,
        int maturityLevel,
        String maturityTier,
        List<SammDomainScore> domainScores,
        Map<Integer, Integer> levelDistribution
) {
    public SammDepartmentScore {
        requireNonNull(departmentId, "departmentId must not be null");
        domainScores = List.copyOf(domainScores != null ? domainScores : List.of());
        levelDistribution = Map.copyOf(levelDistribution != null ? levelDistribution : Map.of());
    }
}
