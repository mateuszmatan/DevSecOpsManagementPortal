package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Optional;

public interface BusinessServiceUsage extends DepartmentUsage {

    default Optional<String> deletionRefusalForService(String businessService) {
        return deletionRefusal(businessService);
    }
}
