package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.report.port.in.ManagePipelineReportUseCase.RecordReportCommand;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;

public record UploadReportRequest(
        String pipelineKey,
        String job,
        Long build,
        String result,
        String branch,
        String commit,
        Long durationSeconds,
        Long stagesTotal,
        Long passed,
        Long warned,
        Long failed,
        Long blocked,
        Long skipped,
        String reportHtml
) {

    public RecordReportCommand toCommand() {
        RunResult runResult = parseResult(result);
        return new RecordReportCommand(
                pipelineKey,
                job,
                build,
                runResult,
                branch,
                commit,
                durationSeconds,
                Instant.now(),
                stagesTotal,
                passed,
                warned,
                failed,
                blocked,
                skipped,
                reportHtml
        );
    }

    private static RunResult parseResult(String value) {
        if (value == null || value.isBlank()) {
            return RunResult.SUCCESS;
        }
        try {
            return RunResult.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RunResult.SUCCESS;
        }
    }
}
