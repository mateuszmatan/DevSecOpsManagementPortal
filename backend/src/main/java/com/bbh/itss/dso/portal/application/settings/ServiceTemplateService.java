package com.bbh.itss.dso.portal.application.settings;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageServiceTemplateUseCase;
import com.bbh.itss.dso.portal.application.settings.port.out.ServiceTemplateRepositoryPort;
import com.bbh.itss.dso.portal.domain.settings.ServiceTemplate;
import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class ServiceTemplateService implements ManageServiceTemplateUseCase {

    private final ServiceTemplateRepositoryPort repository;

    @Override
    @ReadOnly
    public StoredServiceTemplate current() {
        return stored();
    }

    @Override
    public StoredServiceTemplate update(Long expectedVersion, ServiceTemplate template) {
        return repository.save(stored().change(expectedVersion, template));
    }

    private StoredServiceTemplate stored() {
        return repository.load().orElseGet(StoredServiceTemplate::unsaved);
    }
}
