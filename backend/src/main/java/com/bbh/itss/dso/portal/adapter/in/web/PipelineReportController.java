package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.report.port.in.ManagePipelineReportUseCase;
import com.bbh.itss.dso.portal.domain.report.PipelineReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PipelineReportController {

    private final ManagePipelineReportUseCase reports;

    @PostMapping("/dso/reports")
    @ResponseStatus(CREATED)
    public PipelineReportResponse uploadReport(@RequestBody UploadReportRequest request) {
        PipelineReport saved = reports.recordReport(request.toCommand());
        return PipelineReportResponse.of(saved);
    }

    @GetMapping("/pipelines/{id}/report")
    public ResponseEntity<PipelineReportResponse> getReport(
            @PathVariable long id,
            @RequestParam(required = false) Long build
    ) {
        var found = build != null ? reports.getReportByBuild(id, build) : reports.getLatestReport(id);
        return found.map(r -> ResponseEntity.ok(PipelineReportResponse.of(r)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping(value = "/pipelines/{id}/report/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLatestReportHtml(@PathVariable long id) {
        String html = reports.getLatestReportHtml(id);
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = "/pipelines/{id}/runs/{build}/report/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getReportHtmlByBuild(@PathVariable long id, @PathVariable long build) {
        String html = reports.getReportHtmlByBuild(id, build);
        return ResponseEntity.ok(html);
    }
}
