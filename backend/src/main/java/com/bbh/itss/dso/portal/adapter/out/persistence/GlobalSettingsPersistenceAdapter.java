package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.MonitoringSettingsStore;
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.bbh.itss.dso.portal.adapter.out.persistence.GlobalSettingsEntity.ID;
import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;

@Component
class GlobalSettingsPersistenceAdapter implements GlobalSettingsRepositoryPort {

    private final GlobalSettingsJpaRepository repository;
    private final MonitoringSettingsStore monitoring;

    @Autowired
    GlobalSettingsPersistenceAdapter(GlobalSettingsJpaRepository repository,
                                     Optional<MonitoringSettingsStore> monitoring) {
        this.repository = repository;
        this.monitoring = monitoring.orElseGet(MonitoringSettingsStore::new);
    }

    @Override
    public Optional<GlobalSettings> load() {
        Optional<GlobalSettings> loaded = repository.findById(ID).map(GlobalSettingsEntity::toDomain);
        loaded.map(GlobalSettings::platform).ifPresent(monitoring::update);
        return loaded;
    }

    @Override
    public GlobalSettings save(GlobalSettings settings) {
        GlobalSettingsEntity entity = repository.findById(ID).orElseGet(GlobalSettingsEntity::new);
        if (!entity.isNew() && entity.version() != settings.version()) {
            throw staleVersion();
        }
        entity.apply(settings.values());
        GlobalSettings saved = repository.saveAndFlush(entity).toDomain();
        monitoring.update(saved.platform());
        return saved;
    }
}

