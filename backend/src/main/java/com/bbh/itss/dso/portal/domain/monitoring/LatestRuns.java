package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Comparator.comparing;

public record LatestRuns(Map<MetricsTag, List<PipelineRun>> runs, Set<MetricsTag> sharedTags) {

    public LatestRuns {
        runs = Map.copyOf(runs);
        sharedTags = Set.copyOf(sharedTags);
    }

    public static LatestRuns none() {
        return new LatestRuns(Map.of(), Set.of());
    }

    public PipelineRun of(MetricsTag tag, Pipeline pipeline) {
        boolean shared = sharedTags.contains(tag);
        return runs.getOrDefault(tag, List.of()).stream()
                .filter(run -> !shared || pipeline.settings().builds(run.job()))
                .max(comparing(PipelineRun::time))
                .orElse(null);
    }
}
