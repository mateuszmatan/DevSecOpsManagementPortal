package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.bbh.itss.dso.portal.adapter.out.persistence.SingletonEntity.ID;

@Component
@RequiredArgsConstructor
class GlobalSettingsPersistenceAdapter implements GlobalSettingsRepositoryPort {

    private final GlobalSettingsJpaRepository repository;

    @Override
    public Optional<GlobalSettings> load() {
        return repository.findById(ID).map(GlobalSettingsEntity::toDomain);
    }

    @Override
    public GlobalSettings save(GlobalSettings settings) {
        GlobalSettingsEntity entity = repository.findById(ID).orElseGet(GlobalSettingsEntity::new);
        if (!entity.isNew()) {
            entity.requireVersion(settings.version());
        }
        entity.apply(settings.values());
        return repository.saveAndFlush(entity).toDomain();
    }
}

