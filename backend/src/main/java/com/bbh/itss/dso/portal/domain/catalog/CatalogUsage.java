package com.bbh.itss.dso.portal.domain.catalog;

public record CatalogUsage(long productCount, long serviceCount, long pipelineCount, long activePipelineCount) {

    public static final CatalogUsage UNUSED = new CatalogUsage(0, 0, 0, 0);
}
