package com.bbh.itss.dso.portal.domain.report;

import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record PipelineReport(Long id, Long pipelineId, String pipelineKey, String jobName, Long buildNumber,
                            RunResult result, String branch, String commitHash, Long durationSeconds,
                            Instant finishedAt, Long stagesTotal, Long passed, Long warned, Long failed,
                            Long blocked, Long skipped, String reportHtml) {

    public PipelineReport {
        requireNonNull(finishedAt, "finishedAt must not be null");
    }
}
