package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.report.PipelineReport;

import java.time.Instant;

public record PipelineReportResponse(
        Long id,
        Long pipelineId,
        String pipelineKey,
        String jobName,
        Long buildNumber,
        RunResult result,
        String branch,
        String commitHash,
        Long durationSeconds,
        Instant finishedAt,
        Long stagesTotal,
        Long passed,
        Long warned,
        Long failed,
        Long blocked,
        Long skipped
) {

    public static PipelineReportResponse of(PipelineReport report) {
        return new PipelineReportResponse(
                report.id(),
                report.pipelineId(),
                report.pipelineKey(),
                report.jobName(),
                report.buildNumber(),
                report.result(),
                report.branch(),
                report.commitHash(),
                report.durationSeconds(),
                report.finishedAt(),
                report.stagesTotal(),
                report.passed(),
                report.warned(),
                report.failed(),
                report.blocked(),
                report.skipped()
        );
    }
}
