package com.bbh.itss.dso.portal.domain.catalog;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record Service(Long id, String name, String description, int displayOrder, ServiceSettings settings) {

    public Service {
        name = trim(name);
        description = trimToNull(description);
        requireNonNull(settings, "a service needs its settings");
    }

    public boolean hasId(Long serviceId) {
        return id != null && id.equals(serviceId);
    }
}
