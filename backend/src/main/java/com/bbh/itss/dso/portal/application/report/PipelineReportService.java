package com.bbh.itss.dso.portal.application.report;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.report.port.in.ManagePipelineReportUseCase;
import com.bbh.itss.dso.portal.application.report.port.out.PipelineReportPort;
import com.bbh.itss.dso.portal.domain.report.PipelineReport;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@UseCase
@RequiredArgsConstructor
public class PipelineReportService implements ManagePipelineReportUseCase {

    private final PipelineReportPort reports;

    @Override
    public PipelineReport recordReport(RecordReportCommand command) {
        String key = trimToNull(command.pipelineKey());
        String job = trimToNull(command.jobName());
        Long pipelineId = null;
        if (key != null) {
            pipelineId = reports.findPipelineIdByKey(key).orElse(null);
        }
        if (pipelineId == null && job != null) {
            pipelineId = reports.findPipelineIdByJobName(job).orElse(null);
        }

        Instant finished = command.finishedAt() != null ? command.finishedAt() : Instant.now();
        PipelineReport report = new PipelineReport(
                null,
                pipelineId,
                key,
                job,
                command.buildNumber(),
                command.result(),
                trimToNull(command.branch()),
                trimToNull(command.commitHash()),
                command.durationSeconds(),
                finished,
                command.stagesTotal(),
                command.passed(),
                command.warned(),
                command.failed(),
                command.blocked(),
                command.skipped(),
                command.reportHtml()
        );
        return reports.save(report);
    }

    @Override
    @ReadOnly
    public Optional<PipelineReport> getLatestReport(Long pipelineId) {
        return reports.findLatestByPipelineId(pipelineId);
    }

    @Override
    @ReadOnly
    public Optional<PipelineReport> getReportByBuild(Long pipelineId, Long buildNumber) {
        return reports.findByPipelineIdAndBuild(pipelineId, buildNumber);
    }

    @Override
    @ReadOnly
    public String getLatestReportHtml(Long pipelineId) {
        return getLatestReport(pipelineId)
                .map(PipelineReport::reportHtml)
                .filter(html -> html != null && !html.isBlank())
                .orElseGet(this::emptyReportHtml);
    }

    @Override
    @ReadOnly
    public String getReportHtmlByBuild(Long pipelineId, Long buildNumber) {
        return getReportByBuild(pipelineId, buildNumber)
                .map(PipelineReport::reportHtml)
                .filter(html -> html != null && !html.isBlank())
                .orElseGet(this::emptyReportHtml);
    }

    @Override
    @ReadOnly
    public List<PipelineReport> getRecentReports(Long pipelineId, int limit) {
        return reports.findRecentByPipelineId(pipelineId, limit);
    }

    private String emptyReportHtml() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f8fafc; color: #475569; padding: 32px; text-align: center; }
                .box { max-width: 500px; margin: 40px auto; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
                h3 { color: #0f172a; margin-bottom: 8px; font-size: 1rem; }
                p { font-size: 0.85rem; line-height: 1.5; }
                </style>
                </head>
                <body>
                <div class="box">
                  <h3>No Pipeline Report Uploaded</h3>
                  <p>A report has not been sent by this pipeline yet. The execution report will be displayed here automatically as soon as the pipeline completes in Jenkins.</p>
                </div>
                </body>
                </html>
                """;
    }
}
