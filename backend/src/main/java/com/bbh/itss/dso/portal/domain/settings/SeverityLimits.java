package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

public record SeverityLimits(Integer maxCritical, Integer maxHigh, Integer maxMedium) {

    public static final SeverityLimits ZERO = new SeverityLimits(0, 0, 0);

    public void writeTo(ConfigTree config, String path) {
        config.set(path + ".maxCritical", maxCritical)
                .set(path + ".maxHigh", maxHigh)
                .set(path + ".maxMedium", maxMedium);
    }
}
