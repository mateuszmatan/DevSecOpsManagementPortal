package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public record MonitoringRange(int days) {

    public static final int MAX_DAYS = 730;
    private static final String USAGE = "use a number of days such as 7d, 30d or 90d";
    private static final Pattern DAYS = Pattern.compile("^([1-9][0-9]{0,2})d$");

    public MonitoringRange {
        if (days < 1) {
            throw InvalidRequestException.of("range", USAGE);
        }
        if (days > MAX_DAYS) {
            throw InvalidRequestException.of("range", "can cover at most " + MAX_DAYS + " days");
        }
    }

    public static MonitoringRange parse(String range) {
        Matcher matcher = DAYS.matcher(trimToEmpty(range));
        if (!matcher.matches()) {
            throw InvalidRequestException.of("range", USAGE);
        }
        return new MonitoringRange(parseInt(matcher.group(1)));
    }
}
