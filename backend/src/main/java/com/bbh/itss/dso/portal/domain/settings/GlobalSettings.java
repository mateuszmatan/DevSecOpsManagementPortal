package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Instant;
import java.util.Objects;

import static com.bbh.itss.dso.portal.domain.shared.Versions.requireReadAt;
import static java.util.Objects.requireNonNull;

public record GlobalSettings(GlobalSettingsValues values, long version, Instant updatedAt) {

    public GlobalSettings {
        requireNonNull(values, "values");
    }

    public static GlobalSettings bbhDefaults() {
        return new GlobalSettings(GlobalSettingsValues.bbhDefaults(), 0, null);
    }

    /**
     * The InfluxDB token is never shown, so a change without one keeps the stored token, but only for the same
     * InfluxDB: a new or cleared URL needs its own token, so the stored one is never sent anywhere else.
     */
    public GlobalSettings change(Long expectedVersion, GlobalSettingsValues changed) {
        requireReadAt(expectedVersion, version);
        ValidationProblems problems = new ValidationProblems();
        changed.validate(problems);
        problems.throwIfAny();
        PlatformSettings platform = changed.platform();
        boolean keepsToken = platform.influxToken() == null
                && Objects.equals(platform.influxUrl(), platform().influxUrl());
        return new GlobalSettings(keepsToken
                ? changed.withPlatform(platform.withInfluxToken(platform().influxToken())) : changed, version, updatedAt);
    }

    public PlatformSettings platform() {
        return values.platform();
    }

    public String jenkinsUrl() {
        return values.platform().jenkinsUrl();
    }
}
