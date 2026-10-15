package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Instant;
import java.util.Objects;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;
import static java.util.Objects.requireNonNull;

public record StoredServiceTemplate(ServiceTemplate template, Long version, Instant updatedAt) {

    public StoredServiceTemplate {
        requireNonNull(template, "a stored service template holds its values");
    }

    public static StoredServiceTemplate unsaved() {
        return new StoredServiceTemplate(ServiceTemplate.bbhDefaults(), null, null);
    }

    public StoredServiceTemplate change(Long expectedVersion, ServiceTemplate changed) {
        if (!Objects.equals(expectedVersion, version)) {
            throw staleVersion();
        }
        ValidationProblems problems = new ValidationProblems();
        changed.validate(problems);
        problems.throwIfAny();
        return new StoredServiceTemplate(changed, version, updatedAt);
    }
}
