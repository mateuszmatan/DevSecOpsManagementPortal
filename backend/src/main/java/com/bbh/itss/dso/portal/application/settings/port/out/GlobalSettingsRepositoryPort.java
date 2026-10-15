package com.bbh.itss.dso.portal.application.settings.port.out;

import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;

import java.util.Optional;

public interface GlobalSettingsRepositoryPort {

    Optional<GlobalSettings> load();

    GlobalSettings save(GlobalSettings settings);
}
