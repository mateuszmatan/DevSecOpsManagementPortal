package com.bbh.itss.dso.portal.domain.catalog;

import static java.util.Locale.ROOT;

public enum TestStage {
    SMOKE, REGRESSION, PERFORMANCE;

    public String configKey() {
        return name().toLowerCase(ROOT);
    }
}
