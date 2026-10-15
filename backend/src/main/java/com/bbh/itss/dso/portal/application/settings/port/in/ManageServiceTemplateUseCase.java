package com.bbh.itss.dso.portal.application.settings.port.in;

import com.bbh.itss.dso.portal.domain.settings.ServiceTemplate;
import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate;

public interface ManageServiceTemplateUseCase {

    StoredServiceTemplate current();

    StoredServiceTemplate update(Long expectedVersion, ServiceTemplate template);
}
