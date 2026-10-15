package com.bbh.itss.dso.portal.application.pipeline.port.in;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.util.List;

public interface PipelinesUseCase {

    List<ServicePipelinesView> listForProduct(long productId);

    PipelineView get(long id);

    PipelineView create(long serviceId, PipelineType type, PipelineSettings settings);

    List<PipelineView> createMissing(long productId, List<Long> serviceIds, PipelineType type);

    PipelineView update(long id, Long version, PipelineType type, PipelineSettings settings);

    void delete(long id);

    PipelineView issueKey(long pipelineId);

    PipelineView revokeKey(long pipelineId, String reason);

    long authorizeKey(String keyValue);
}
