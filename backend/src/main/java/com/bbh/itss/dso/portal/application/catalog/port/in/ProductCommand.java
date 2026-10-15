package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public record ProductCommand(Long version, ProductDetails details, AppScanAccount appScan, List<ServiceDraft> services,
                             PipelineType pipelineType) {

    public ProductCommand {
        services = List.copyOf(emptyIfNull(services));
    }
}
