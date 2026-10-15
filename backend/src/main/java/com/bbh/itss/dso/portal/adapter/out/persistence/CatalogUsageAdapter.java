package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentUsagePort;
import com.bbh.itss.dso.portal.domain.catalog.CatalogUsage;
import com.bbh.itss.dso.portal.domain.catalog.DepartmentUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.out.persistence.Counts.perId;
import static com.bbh.itss.dso.portal.domain.catalog.CatalogUsage.UNUSED;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Component
@RequiredArgsConstructor
class CatalogUsageAdapter implements DepartmentUsagePort {

    private final ProductJpaRepository products;
    private final ServiceJpaRepository services;
    private final PipelineJpaRepository pipelines;

    @Override
    public Map<Long, DepartmentUsage> perDepartment() {
        Map<Long, Long> productCounts = perId(products.countByDepartment());
        Map<Long, Long> serviceCounts = perId(services.countByDepartment());
        Map<Long, Long> pipelineCounts = perId(pipelines.countByDepartment());
        Map<Long, Long> activeCounts = perId(pipelines.countWithActiveKeyByDepartment());
        return productCounts.keySet().stream().collect(toMap(identity(), department -> new CatalogUsage(
                productCounts.get(department), serviceCounts.getOrDefault(department, 0L),
                pipelineCounts.getOrDefault(department, 0L), activeCounts.getOrDefault(department, 0L))));
    }

    @Override
    public DepartmentUsage unused() {
        return UNUSED;
    }
}
