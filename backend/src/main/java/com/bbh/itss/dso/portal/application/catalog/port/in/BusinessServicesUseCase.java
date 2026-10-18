package com.bbh.itss.dso.portal.application.catalog.port.in;

import java.util.List;

public interface BusinessServicesUseCase {

    List<BusinessServiceView> listBusinessServices();

    BusinessServiceView createBusinessService(String name);

    BusinessServiceView renameBusinessService(long id, Long version, String name);

    void deleteBusinessService(long id);
}
