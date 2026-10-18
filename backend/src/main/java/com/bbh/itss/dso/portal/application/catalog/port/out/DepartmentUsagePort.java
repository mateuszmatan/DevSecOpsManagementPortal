package com.bbh.itss.dso.portal.application.catalog.port.out;

import com.bbh.itss.dso.portal.domain.catalog.CatalogUsage;

import java.util.Map;

public interface DepartmentUsagePort {

    Map<Long, CatalogUsage> perDepartment();
}
