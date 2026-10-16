package com.bbh.itss.dso.portal.adapter;

import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MonitoringSettingsStore {

    private final AtomicReference<PlatformSettings> platform = new AtomicReference<>();

    public Optional<PlatformSettings> platform() {
        return Optional.ofNullable(platform.get());
    }

    public void update(PlatformSettings settings) {
        platform.set(settings);
    }
}
