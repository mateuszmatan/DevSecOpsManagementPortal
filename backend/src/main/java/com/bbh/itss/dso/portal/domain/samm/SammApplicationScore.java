package com.bbh.itss.dso.portal.domain.samm;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record SammApplicationScore(
        Long pipelineId,
        String pipelineKey,
        String jobName,
        Long productId,
        String productName,
        Long serviceId,
        String serviceName,
        Long departmentId,
        String departmentName,
        double overallScore,
        int maturityLevel,
        String maturityTier,
        List<SammDomainScore> domainScores,
        List<SammPracticeScore> practiceScores,
        Instant assessedAt
) {
    public SammApplicationScore {
        requireNonNull(pipelineId, "pipelineId must not be null");
        domainScores = List.copyOf(domainScores != null ? domainScores : List.of());
        practiceScores = List.copyOf(practiceScores != null ? practiceScores : List.of());
        requireNonNull(assessedAt, "assessedAt must not be null");
    }
}
