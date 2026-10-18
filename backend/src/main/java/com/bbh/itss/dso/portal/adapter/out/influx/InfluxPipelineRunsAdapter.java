package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsRow;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.positive;
import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.string;
import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.strings;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.doraPoint;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.tag;

@Component
@RequiredArgsConstructor
class InfluxPipelineRunsAdapter implements PipelineRunsPort {

    private final InfluxQueryClient influx;

    @Override
    public boolean configured(PlatformSettings platform) {
        return influx.configured(platform);
    }

    @Override
    public void ping(PlatformSettings platform) {
        influx.read(platform, () -> influx.query(platform, "buckets() |> limit(n: 1)"));
    }

    @Override
    public LatestRuns latestRuns(PlatformSettings platform, Collection<MetricsTag> tags, Set<MetricsTag> sharedTags) {
        if (tags.isEmpty()) {
            influx.requireConfigured(platform);
            return LatestRuns.none();
        }
        List<MetricsTag> byTag = tags.stream().filter(tag -> !sharedTags.contains(tag)).toList();
        List<MetricsTag> byJob = tags.stream().filter(sharedTags::contains).toList();
        return influx.read(platform, () -> {
            Map<MetricsTag, List<PipelineRun>> runs = new HashMap<>();
            collect(platform, runs, byTag, """
                      |> last()
                      |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                      |> group(columns: ["project", "env"])
                    """);
            collect(platform, runs, byJob, """
                      |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                      |> group(columns: ["project", "env", "job"])
                    """);
            return new LatestRuns(runs, sharedTags);
        });
    }

    private void collect(PlatformSettings platform, Map<MetricsTag, List<PipelineRun>> runs, List<MetricsTag> tags,
                         String grouping) {
        if (tags.isEmpty()) {
            return;
        }
        String flux = """
                from(bucket: %s)
                  |> range(start: -%s)
                  |> filter(fn: (r) => r._measurement == "pipeline_run")
                  |> filter(fn: (r) => contains(value: r.project, set: [%s]))
                %s  |> sort(columns: ["_time"])
                  |> last(column: "_time")
                """.formatted(influx.bucket(platform), influx.lastRunLookback(),
                strings(tags.stream().map(MetricsTag::project).toList()), grouping);
        for (Map<String, String> row : influx.query(platform, flux)) {
            MetricsTag tag = tag(row);
            if (tags.contains(tag)) {
                runs.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(MetricsRow.run(row));
            }
        }
    }

    @Override
    public List<PipelineRun> recentRuns(PlatformSettings platform, MetricsTag tag, String job, int days, int limit) {
        String byJob = job == null ? "" : """
                  |> filter(fn: (r) => exists r.job and (r.job == %s or strings.hasPrefix(v: r.job, prefix: %s)))
                """.formatted(string(job), string(job + "/"));
        String flux = """
                %sfrom(bucket: %s)
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "pipeline_run")
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                %s  |> group()
                  |> sort(columns: ["_time"], desc: true)
                  |> limit(n: %d)
                """.formatted(job == null ? "" : "import \"strings\"\n\n", influx.bucket(platform), positive(days),
                string(tag.project()), string(tag.env()), byJob, positive(limit));
        return influx.read(platform, () -> influx.query(platform, flux).stream().map(MetricsRow::run).toList());
    }

    @Override
    public Map<MetricsTag, List<DoraPoint>> doraPoints(PlatformSettings platform, Collection<MetricsTag> tags,
                                                       int days) {
        if (tags.isEmpty()) {
            influx.requireConfigured(platform);
            return Map.of();
        }
        String flux = """
                from(bucket: %s)
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "dora")
                  |> filter(fn: (r) => contains(value: r.project, set: [%s]))
                  |> filter(fn: (r) => r._field == "deployment" or r._field == "change_failure" \
                or r._field == "lead_time_s" or r._field == "duration_s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group()
                  |> sort(columns: ["_time"])
                """.formatted(influx.bucket(platform), positive(days),
                strings(tags.stream().map(MetricsTag::project).toList()));
        return influx.read(platform, () -> {
            Map<MetricsTag, List<DoraPoint>> points = new HashMap<>();
            for (Map<String, String> row : influx.query(platform, flux)) {
                MetricsTag tag = tag(row);
                DoraPoint point = doraPoint(row);
                if (point != null && tags.contains(tag)) {
                    points.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(point);
                }
            }
            return points;
        });
    }
}
