package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.DepartmentUsage;

public record BusinessServiceView(long id, String name, long version, DepartmentUsage usage) {

    public DepartmentView toDepartmentView() {
        return new DepartmentView(id, name, version, usage);
    }
}
