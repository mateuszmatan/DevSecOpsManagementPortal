package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.fromTag;
import static java.lang.Double.parseDouble;
import static java.lang.Math.floor;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@NoArgsConstructor(access = PRIVATE)
final class MetricsRow {

    static MetricsTag tag(Map<String, String> row) {
        return new MetricsTag(row.get("project"), row.get("env"));
    }

    static PipelineRun run(Map<String, String> row) {
        return new PipelineRun(Instant.parse(row.get("_time")), fromTag(row.get("result")),
                trimToNull(row.get("branch")), number(row.get("build")), number(row.get("duration_s")),
                trimToNull(row.get("commit")), trimToNull(row.get("job")), number(row.get("stages_total")),
                number(row.get("passed")), number(row.get("not_required")), number(row.get("warned")),
                number(row.get("failed")), number(row.get("blocked")), number(row.get("skipped")));
    }

    static DoraPoint doraPoint(Map<String, String> row) {
        if (row.get("_time") == null) {
            return null;
        }
        return new DoraPoint(Instant.parse(row.get("_time")), positive(row.get("deployment")),
                positive(row.get("change_failure")), getIfNull(number(row.get("lead_time_s")), 0L),
                getIfNull(number(row.get("duration_s")), 0L));
    }

    static Long number(String value) {
        Double decimal = decimal(value);
        return decimal == null ? null : (long) floor(decimal);
    }

    static Double decimal(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean positive(String value) {
        Long number = number(value);
        return number != null && number > 0;
    }
}
