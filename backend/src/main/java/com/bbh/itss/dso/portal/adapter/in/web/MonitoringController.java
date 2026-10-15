package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringOverview;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringStatus;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineHealth;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineMonitoring;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PortfolioActivity;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductHealth;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductMonitoring;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.DashboardLink;
import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.in.web.PipelineResponse.monitored;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitorPipelinesUseCase monitoring;

    @GetMapping("/status")
    public StatusResponse status() {
        MonitoringStatus status = monitoring.status();
        return new StatusResponse(status.metricsConfigured(), status.metricsReachable(), status.metricsError(),
                status.dashboards().stream().map(Grafana::of).toList());
    }

    @GetMapping("/products")
    public OverviewResponse overview() {
        MonitoringOverview overview = monitoring.overview();
        return new OverviewResponse(overview.products().stream().map(ProductHealthResponse::of).toList(),
                overview.metricsError());
    }

    @GetMapping("/products/{id}")
    public ProductMonitoringResponse product(@PathVariable long id) {
        ProductMonitoring found = monitoring.product(id);
        Product product = found.product();
        return new ProductMonitoringResponse(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), found.overall(),
                found.pipelines().stream().map(PipelineHealthResponse::of).toList(), found.metricsError());
    }

    @GetMapping("/pipelines/{id}")
    public PipelineMonitoringResponse pipeline(@PathVariable long id,
                                               @RequestParam(defaultValue = "30d") String range) {
        PipelineMonitoring found = monitoring.pipeline(id, range);
        PipelineView view = found.pipeline();
        return new PipelineMonitoringResponse(monitored(view), found.status(),
                RunResponse.of(found.lastRun(), view), found.dora(),
                found.recentRuns().stream().map(run -> RunResponse.of(run, view)).toList(),
                found.dashboards().stream().map(Grafana::of).toList(), found.metricsError());
    }

    @GetMapping("/activity")
    public PortfolioActivity activity(@RequestParam(defaultValue = "30d") String range) {
        return monitoring.activity(range);
    }

    public record StatusResponse(boolean influxConfigured, boolean influxReachable, String influxError,
                                 List<Grafana> grafana) {
    }

    public record OverviewResponse(List<ProductHealthResponse> products, String metricsError) {
    }

    public record ProductHealthResponse(Long productId, String code, String name, String ownerTeam,
                                        Long departmentId, int serviceCount, int pipelineCount, RunResult overall,
                                        Map<RunResult, Integer> statusCounts, Instant lastRunAt) {

        static ProductHealthResponse of(ProductHealth health) {
            Product product = health.product();
            return new ProductHealthResponse(product.id(), product.code(), product.name(), product.ownerTeam(),
                    product.departmentId(), product.services().size(), health.pipelineCount(),
                    health.overall(), health.statusCounts(), health.lastRunAt());
        }
    }

    public record ProductMonitoringResponse(Long productId, String code, String name, String description,
                                            String ownerTeam, RunResult overall,
                                            List<PipelineHealthResponse> pipelines, String metricsError) {
    }

    public record PipelineHealthResponse(PipelineResponse pipeline, RunResult status, RunResponse lastRun) {

        static PipelineHealthResponse of(PipelineHealth health) {
            return new PipelineHealthResponse(monitored(health.pipeline()), health.status(),
                    RunResponse.of(health.lastRun(), health.pipeline()));
        }
    }

    public record PipelineMonitoringResponse(PipelineResponse pipeline, RunResult status, RunResponse lastRun,
                                             DoraSummary dora, List<RunResponse> recentRuns, List<Grafana> grafana,
                                             String metricsError) {
    }

    public record RunResponse(Instant time, RunResult result, String branch, Long build, Long durationSeconds,
                              String commit, String job, String buildUrl, Long stagesTotal, Long passed, Long warned,
                              Long failed, Long blocked, Long skipped) {

        static RunResponse of(PipelineRun run, PipelineView view) {
            return run == null ? null : new RunResponse(run.time(), run.result(), run.branch(), run.build(),
                    run.durationSeconds(), run.commit(), run.job(), view.buildUrl(run), run.stagesTotal(),
                    run.passed(), run.warned(), run.failed(), run.blocked(), run.skipped());
        }
    }

    public record Grafana(String name, String dashboardUrl) {

        static Grafana of(DashboardLink link) {
            return new Grafana(link.name(), link.url());
        }
    }
}
