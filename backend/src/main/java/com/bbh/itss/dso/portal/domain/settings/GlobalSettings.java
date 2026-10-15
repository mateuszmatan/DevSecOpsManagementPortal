package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Instant;

import static com.bbh.itss.dso.portal.domain.shared.Versions.requireCurrent;
import static java.util.Objects.requireNonNull;

public record GlobalSettings(GlobalSettingsValues values, long version, Instant updatedAt) {

    public GlobalSettings {
        requireNonNull(values, "values");
    }

    public static GlobalSettings bbhDefaults() {
        return new GlobalSettings(GlobalSettingsValues.bbhDefaults(), 0, null);
    }

    public GlobalSettings change(Long expectedVersion, GlobalSettingsValues changed) {
        requireCurrent(expectedVersion, version);
        ValidationProblems problems = new ValidationProblems();
        changed.validate(problems);
        problems.throwIfAny();
        return new GlobalSettings(changed, version, updatedAt);
    }

    public PlatformSettings platform() {
        return values.platform();
    }

    public String jenkinsUrl() {
        return values.platform().jenkinsUrl();
    }
}
