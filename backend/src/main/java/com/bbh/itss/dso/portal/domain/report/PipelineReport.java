package com.bbh.itss.dso.portal.domain.report;

import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record PipelineReport(Long id, Long pipelineId, String pipelineKey, String jobName, Long buildNumber,
                            RunResult result, String branch, String commitHash, Long durationSeconds,
                            Instant finishedAt, Long stagesTotal, Long passed, Long warned, Long failed,
                            Long blocked, Long skipped, String reportHtml, List<AutoResolvedSetting> autoResolved) {

    public PipelineReport {
        requireNonNull(finishedAt, "finishedAt must not be null");
        autoResolved = autoResolved == null ? List.of() : List.copyOf(autoResolved);
    }

    public PipelineReport(Long id, Long pipelineId, String pipelineKey, String jobName, Long buildNumber,
                          RunResult result, String branch, String commitHash, Long durationSeconds,
                          Instant finishedAt, Long stagesTotal, Long passed, Long warned, Long failed,
                          Long blocked, Long skipped, String reportHtml) {
        this(id, pipelineId, pipelineKey, jobName, buildNumber, result, branch, commitHash, durationSeconds,
                finishedAt, stagesTotal, passed, warned, failed, blocked, skipped, reportHtml, List.of());
    }
}
