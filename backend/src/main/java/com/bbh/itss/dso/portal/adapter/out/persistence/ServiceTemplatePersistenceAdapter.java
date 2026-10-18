package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.settings.port.out.ServiceTemplateRepositoryPort;
import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.bbh.itss.dso.portal.adapter.out.persistence.SingletonEntity.ID;

@Component
@RequiredArgsConstructor
class ServiceTemplatePersistenceAdapter implements ServiceTemplateRepositoryPort {

    private final ServiceTemplateJpaRepository repository;

    @Override
    public Optional<StoredServiceTemplate> load() {
        return repository.findById(ID).map(ServiceTemplateEntity::toDomain);
    }

    @Override
    public StoredServiceTemplate save(StoredServiceTemplate template) {
        ServiceTemplateEntity entity = repository.findById(ID).orElseGet(ServiceTemplateEntity::new);
        entity.requireVersion(template.version());
        entity.apply(template.template());
        return repository.saveAndFlush(entity).toDomain();
    }
}
