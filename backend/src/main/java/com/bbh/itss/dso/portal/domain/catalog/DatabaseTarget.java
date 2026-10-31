package com.bbh.itss.dso.portal.domain.catalog;

import static java.util.Locale.ROOT;

public enum DatabaseTarget {
    TEST, RD, QC;

    public String configKey() {
        return name().toLowerCase(ROOT);
    }
}
