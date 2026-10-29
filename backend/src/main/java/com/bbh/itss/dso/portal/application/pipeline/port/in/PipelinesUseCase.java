package com.bbh.itss.dso.portal.application.pipeline.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.util.List;

public interface PipelinesUseCase {

    List<ServicePipelinesView> listForProduct(long productId);

    PipelineView get(long id);

    Product productOfService(long serviceId);

    PipelineView create(long serviceId, PipelineType type, PipelineSettings settings);

    /**
     * Creates the pipelines of this type that the services lack, with the given agent labels or, without any, with the
     * service template's.
     */
    List<PipelineView> createMissing(long productId, List<Long> serviceIds, PipelineType type,
                                     List<String> agentLabels);

    /**
     * Refuses a product whose service keeps a pipeline that would fail: a Nexus IQ GoldenFix pipeline without a Nexus
     * IQ application to scan, or a Full pipeline deploying to virtual machines without an UrbanCode Deploy application
     * for DV.
     */
    void requireRunnablePipelines(Product product);

    PipelineView update(long id, Long version, PipelineType type, PipelineSettings settings);

    void delete(long id);

    PipelineView issueKey(long pipelineId);

    PipelineView revokeKey(long pipelineId, String reason);

    long authorizeKey(String keyValue);
}
