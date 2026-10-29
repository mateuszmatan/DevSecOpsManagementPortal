package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

/**
 * A product to store. The pipeline type and the agent labels apply to the pipelines this call creates; without labels
 * they keep the service template's.
 */
public record ProductCommand(Long version, ProductDetails details, List<ServiceDraft> services,
                             PipelineType pipelineType, List<String> agentLabels) {

    public ProductCommand {
        services = List.copyOf(emptyIfNull(services));
        agentLabels = clean(agentLabels);
    }
}
