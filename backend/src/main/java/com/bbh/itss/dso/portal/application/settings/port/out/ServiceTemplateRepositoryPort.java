package com.bbh.itss.dso.portal.application.settings.port.out;

import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate;

import java.util.Optional;

public interface ServiceTemplateRepositoryPort {

    Optional<StoredServiceTemplate> load();

    StoredServiceTemplate save(StoredServiceTemplate template);
}
