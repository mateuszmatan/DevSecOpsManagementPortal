package com.bbh.itss.dso.portal.domain.samm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public record SammOrganizationOverview(
        int totalApplications,
        int totalDepartments,
        double overallScore,
        int maturityLevel,
        String maturityTier,
        List<SammDomainScore> domainScores,
        List<SammDepartmentScore> departments,
        List<SammApplicationScore> applications,
        Map<Integer, Integer> levelDistribution,
        Instant generatedAt
) {
    public SammOrganizationOverview {
        domainScores = List.copyOf(domainScores != null ? domainScores : List.of());
        departments = List.copyOf(departments != null ? departments : List.of());
        applications = List.copyOf(applications != null ? applications : List.of());
        levelDistribution = Map.copyOf(levelDistribution != null ? levelDistribution : Map.of());
        requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
