package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;

import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.Validate.isTrue;

public record MonitoringTargets(List<Product> products, List<PipelineView> pipelines, PlatformSettings platform,
                                Set<MetricsTag> sharedTags) {

    public MonitoringTargets {
        products = List.copyOf(products);
        pipelines = List.copyOf(pipelines);
        sharedTags = Set.copyOf(sharedTags);
        requireNonNull(platform, "monitoring targets carry the platform settings their links are built on");
    }

    public Product product() {
        isTrue(products.size() == 1, "these monitoring targets are not those of one product");
        return products.get(0);
    }

    public Set<MetricsTag> tags() {
        return pipelines.stream().map(PipelineView::metricsTag).collect(toSet());
    }

    public PipelineView pipeline() {
        isTrue(pipelines.size() == 1, "these monitoring targets are not those of one pipeline");
        return pipelines.get(0);
    }
}
