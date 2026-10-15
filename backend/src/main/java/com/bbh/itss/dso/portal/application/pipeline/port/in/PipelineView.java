package com.bbh.itss.dso.portal.application.pipeline.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;

import static java.util.Objects.requireNonNull;

public record PipelineView(Product product, Service service, Pipeline pipeline, String jenkinsUrl) {

    public PipelineView {
        requireNonNull(product, "a pipeline view needs the product");
        requireNonNull(service, "a pipeline view needs the service");
        requireNonNull(pipeline, "a pipeline view needs the pipeline");
    }

    public static PipelineView of(Product product, Pipeline pipeline, String jenkinsUrl) {
        Service service = product.service(pipeline.service().serviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "pipeline " + pipeline.id() + " belongs to no service of product " + product.id()));
        return new PipelineView(product, service, pipeline, jenkinsUrl);
    }

    public String jenkinsJobUrl() {
        return pipeline.settings().jenkinsJobUrl(jenkinsUrl);
    }

    public String buildUrl(PipelineRun run) {
        return run == null ? null : run.buildUrl(jenkinsUrl, pipeline.settings().jenkinsJob());
    }

    public MetricsTag metricsTag() {
        return MetricsTag.of(service, pipeline);
    }
}
