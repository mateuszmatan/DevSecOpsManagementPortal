package com.bbh.itss.dso.portal.application.monitoring;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@UseCase
@RequiredArgsConstructor
public class MonitoringTargetsService implements ReadMonitoringTargetsUseCase {

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final ManageGlobalSettingsUseCase settings;

    @Override
    @ReadOnly
    public MonitoringTargets everything() {
        return targets(products.findAll(), pipelines.findAll());
    }

    @Override
    @ReadOnly
    public MonitoringTargets ofDepartment(long departmentId) {
        if (!products.departmentExists(departmentId)) {
            throw notFound("Department", departmentId);
        }
        return targets(products.findByDepartmentId(departmentId), pipelines.findByDepartmentId(departmentId));
    }

    @Override
    @ReadOnly
    public MonitoringTargets ofProduct(long productId) {
        return targets(List.of(product(productId)), pipelines.findByProductId(productId));
    }

    @Override
    @ReadOnly
    public MonitoringTargets ofPipeline(long pipelineId) {
        Pipeline pipeline = pipelines.load(pipelineId).orElseThrow(() -> notFound("Pipeline", pipelineId));
        return targets(List.of(product(pipeline.service().productId())), List.of(pipeline));
    }

    private MonitoringTargets targets(List<Product> found, List<Pipeline> monitored) {
        Map<Long, Product> byId = found.stream().collect(toMap(Product::id, identity()));
        PlatformSettings platform = settings.current().platform();
        List<PipelineView> views = monitored.stream()
                .filter(pipeline -> byId.containsKey(pipeline.service().productId()))
                .map(pipeline -> PipelineView.of(byId.get(pipeline.service().productId()), pipeline,
                        platform.jenkinsUrl()))
                .toList();
        return new MonitoringTargets(found, views, platform, pipelines.sharedMetricsTags());
    }

    private Product product(long productId) {
        return products.load(productId).orElseThrow(() -> notFound("Product", productId));
    }
}
