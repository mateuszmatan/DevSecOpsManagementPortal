package com.bbh.itss.dso.portal.application.pipeline.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Service;

import java.util.List;

public record ServicePipelinesView(Service service, List<PipelineView> pipelines) {

    public ServicePipelinesView {
        pipelines = List.copyOf(pipelines);
    }
}
