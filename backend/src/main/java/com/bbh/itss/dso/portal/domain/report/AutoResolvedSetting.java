package com.bbh.itss.dso.portal.domain.report;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.defaultString;

public record AutoResolvedSetting(String service, String field, String value, String reason) {

    private static final String COLUMN = "\t";
    private static final String ROW = "\n";

    public AutoResolvedSetting {
        service = clean(service);
        field = clean(field);
        value = clean(value);
        reason = clean(reason);
    }

    public static String encode(List<AutoResolvedSetting> settings) {
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        List<String> rows = new ArrayList<>();
        for (AutoResolvedSetting s : settings) {
            rows.add(String.join(COLUMN, s.service, s.field, s.value, s.reason));
        }
        return String.join(ROW, rows);
    }

    public static List<AutoResolvedSetting> decode(String stored) {
        List<AutoResolvedSetting> settings = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            return settings;
        }
        for (String row : stored.split(ROW)) {
            String[] parts = row.split(COLUMN, -1);
            if (parts.length == 4) {
                settings.add(new AutoResolvedSetting(parts[0], parts[1], parts[2], parts[3]));
            }
        }
        return settings;
    }

    private static String clean(String text) {
        return defaultString(text).replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
