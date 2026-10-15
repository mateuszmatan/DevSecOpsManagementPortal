package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.monitoring.DashboardLink;
import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.util.List;

public record PipelineMonitoring(PipelineView pipeline, RunResult status, PipelineRun lastRun, DoraSummary dora,
                                 List<PipelineRun> recentRuns, List<DashboardLink> dashboards, String metricsError) {

    public PipelineMonitoring {
        recentRuns = List.copyOf(recentRuns);
        dashboards = List.copyOf(dashboards);
    }
}
