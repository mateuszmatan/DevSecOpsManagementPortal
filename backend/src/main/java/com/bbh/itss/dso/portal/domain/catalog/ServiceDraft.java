package com.bbh.itss.dso.portal.domain.catalog;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ServiceDraft(Long id, String name, String description, ServiceSettings settings) {

    public static final int DESCRIPTION_MAX = 2000;

    public ServiceDraft {
        name = trim(name);
        description = trimToNull(description);
        requireNonNull(settings, "a service needs its settings");
    }

    Service place(int displayOrder, String productCode) {
        return new Service(id, name, description, displayOrder, settings.withDefaultMetricsProject(productCode, name));
    }
}
