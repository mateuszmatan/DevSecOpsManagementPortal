package com.bbh.itss.dso.portal.application.monitoring;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.monitoring.port.in.DepartmentPipelines;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringOverview;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringStatus;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineHealth;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineMonitoring;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PortfolioActivity;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductHealth;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductMonitoring;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort;
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary;
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsReading;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.MonitoringRange;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.monitoring.DoraCalculator.summarize;
import static com.bbh.itss.dso.portal.domain.monitoring.DoraCalculator.summarizeAll;
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.worst;
import static java.time.Instant.now;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.ObjectUtils.max;

@UseCase
@RequiredArgsConstructor
public class PipelineMonitoringService implements MonitorPipelinesUseCase {

    static final int RECENT_RUNS = 25;

    private final ReadMonitoringTargetsUseCase targets;
    private final PipelineRunsPort runs;
    private final DashboardLinksPort dashboards;
    private final Clock clock;

    @Override
    @WithoutTransaction
    public MonitoringStatus status() {
        boolean configured = runs.configured();
        String error = configured ? MetricsReading.of(this::ping, false).error() : null;
        return new MonitoringStatus(configured, configured && error == null, error, dashboards.instances());
    }

    @Override
    @WithoutTransaction
    public MonitoringOverview overview() {
        MonitoringTargets monitored = targets.everything();
        List<PipelineView> views = monitored.pipelines();
        MetricsReading<LatestRuns> latest = latestRuns(monitored);
        Map<Long, List<PipelineView>> byProduct = views.stream()
                .collect(groupingBy(view -> view.product().id()));
        List<ProductHealth> health = monitored.products().stream()
                .map(product -> health(product, byProduct.getOrDefault(product.id(), List.of()), latest.value()))
                .toList();
        return new MonitoringOverview(health, latest.error());
    }

    @Override
    @WithoutTransaction
    public DepartmentPipelines department(long departmentId) {
        MonitoringTargets monitored = targets.ofDepartment(departmentId);
        MetricsReading<LatestRuns> latest = latestRuns(monitored);
        return new DepartmentPipelines(health(monitored.pipelines(), latest.value()), latest.error());
    }

    @Override
    @WithoutTransaction
    public ProductMonitoring product(long productId) {
        MonitoringTargets monitored = targets.ofProduct(productId);
        MetricsReading<LatestRuns> latest = latestRuns(monitored);
        List<PipelineHealth> health = health(monitored.pipelines(), latest.value());
        return new ProductMonitoring(monitored.product(), worst(health.stream().map(PipelineHealth::status).toList()),
                health, latest.error());
    }

    @Override
    @WithoutTransaction
    public PipelineMonitoring pipeline(long pipelineId, String range) {
        int days = MonitoringRange.parse(range).days();
        MonitoringTargets monitored = targets.ofPipeline(pipelineId);
        PipelineView view = monitored.pipeline();
        Pipeline pipeline = view.pipeline();
        MetricsTag tag = view.metricsTag();
        boolean shared = monitored.sharedTags().contains(tag);
        String job = shared ? pipeline.settings().jobPath() : null;
        boolean attributable = !shared || job != null;

        MetricsReading<List<PipelineRun>> recent = attributable
                ? MetricsReading.of(() -> runs.recentRuns(tag, job, days, RECENT_RUNS), List.of())
                : new MetricsReading<>(List.of(), null);
        MetricsReading<List<DoraPoint>> points = recent.failed() || !attributable
                ? new MetricsReading<>(List.of(), recent.error())
                : MetricsReading.of(() -> runs.doraPoints(List.of(tag), days).getOrDefault(tag, List.of()), List.of());
        List<PipelineRun> recentRuns = recent.value();
        PipelineRun last = recentRuns.isEmpty() ? null : recentRuns.get(0);
        String error = points.error();
        if (last == null && attributable && !recent.failed()) {
            MetricsReading<LatestRuns> latest = latestRuns(monitored);
            last = latest.value().of(tag, pipeline);
            error = getIfNull(error, latest.error());
        }
        DoraSummary dora = summarize(points.value(), days, now(clock));
        return new PipelineMonitoring(view, RunResult.of(pipeline, last), last, dora, recentRuns,
                dashboards.dashboards(tag, pipeline.type(), days), error);
    }

    @Override
    @WithoutTransaction
    public PortfolioActivity activity(String range) {
        int days = MonitoringRange.parse(range).days();
        MonitoringTargets monitored = targets.everything();
        MetricsReading<Map<MetricsTag, List<DoraPoint>>> points =
                MetricsReading.of(() -> runs.doraPoints(monitored.tags(), days), Map.of());
        return new PortfolioActivity(monitored.pipelines().size(),
                summarizeAll(points.value().values(), days, now(clock)), points.error());
    }

    private boolean ping() {
        runs.ping();
        return true;
    }

    private MetricsReading<LatestRuns> latestRuns(MonitoringTargets monitored) {
        return MetricsReading.of(() -> runs.latestRuns(monitored.tags(), monitored.sharedTags()), LatestRuns.none());
    }

    private static List<PipelineHealth> health(List<PipelineView> views, LatestRuns latest) {
        return views.stream()
                .map(view -> {
                    PipelineRun run = latest.of(view.metricsTag(), view.pipeline());
                    return new PipelineHealth(view, RunResult.of(view.pipeline(), run), run);
                })
                .toList();
    }

    private static ProductHealth health(Product product, List<PipelineView> productPipelines, LatestRuns latest) {
        Map<RunResult, Integer> counts = new EnumMap<>(RunResult.class);
        Instant lastRunAt = null;
        for (PipelineView view : productPipelines) {
            PipelineRun run = latest.of(view.metricsTag(), view.pipeline());
            counts.merge(RunResult.of(view.pipeline(), run), 1, Integer::sum);
            if (run != null) {
                lastRunAt = max(lastRunAt, run.time());
            }
        }
        return new ProductHealth(product, productPipelines.size(), worst(counts.keySet()), counts, lastRunAt);
    }
}
