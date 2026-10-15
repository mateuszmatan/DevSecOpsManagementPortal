package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.util.List;

public record ProductMonitoring(Product product, RunResult overall, List<PipelineHealth> pipelines,
                                String metricsError) {

    public ProductMonitoring {
        pipelines = List.copyOf(pipelines);
    }
}
