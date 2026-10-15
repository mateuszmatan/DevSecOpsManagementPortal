package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

public record MetricsTag(String project, String env) {

    public static MetricsTag of(Service service, Pipeline pipeline) {
        MetricsSettings metrics = service.settings().metrics();
        return of(metrics.influxProject(), metrics.influxEnv(), pipeline.type());
    }

    public static MetricsTag of(String influxProject, String influxEnv, PipelineType type) {
        return new MetricsTag(type.influxProjectTag(influxProject), influxEnv);
    }
}
