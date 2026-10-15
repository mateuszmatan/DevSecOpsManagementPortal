package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary.DailyActivity;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.ELITE;
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.HIGH;
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.LOW;
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.MEDIUM;
import static java.lang.Math.max;
import static java.lang.Math.round;
import static java.time.ZoneOffset.UTC;
import static java.util.Comparator.comparing;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.ObjectUtils.min;

@NoArgsConstructor(access = PRIVATE)
public final class DoraCalculator {

    private static final long HOUR = 3_600;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;

    public static DoraSummary summarize(List<DoraPoint> points, int rangeDays, Instant now) {
        return summarizeAll(List.of(points), rangeDays, now);
    }

    public static DoraSummary summarizeAll(Collection<List<DoraPoint>> series, int rangeDays, Instant now) {
        Instant firstDay = LocalDate.ofInstant(now, UTC).minusDays(max(rangeDays - 1, 0)).atStartOfDay(UTC).toInstant();
        List<List<DoraPoint>> inRange = series.stream()
                .map(one -> one.stream().filter(point -> !point.time().isBefore(firstDay)).toList()).toList();
        List<DoraPoint> points = inRange.stream().flatMap(List::stream).sorted(comparing(DoraPoint::time)).toList();
        int runs = points.size();
        int deployments = (int) points.stream().filter(DoraPoint::deployment).count();
        Double perWeek = rangeDays > 0 ? deployments * 7.0 / rangeDays : null;

        List<DoraPoint> deployed = points.stream().filter(DoraPoint::deployment).toList();
        List<Long> leadTimes = deployed.stream().map(DoraPoint::leadTimeSeconds).filter(s -> s > 0).sorted().toList();
        Long leadMedian = median(leadTimes);

        Double cfr = deployments == 0 ? null
                : 100.0 * deployed.stream().filter(DoraPoint::changeFailure).count() / deployments;

        List<Long> restoreTimes = new ArrayList<>();
        Instant failingSince = null;
        for (List<DoraPoint> one : inRange) {
            failingSince = min(failingSince, restores(one, restoreTimes));
        }
        Long mttr = restoreTimes.isEmpty() ? null
                : round(restoreTimes.stream().mapToLong(Long::longValue).average().orElse(0));

        Long averageDuration = runs == 0 ? null
                : round(points.stream().mapToLong(DoraPoint::durationSeconds).average().orElse(0));

        return new DoraSummary(rangeDays, runs, deployments, perWeek,
                runs == 0 || perWeek == null ? null : deploymentFrequencyLevel(perWeek),
                leadMedian, leadMedian == null ? null : leadTimeLevel(leadMedian),
                cfr, cfr == null ? null : changeFailureRateLevel(cfr),
                mttr, mttr == null ? null : timeToRestoreLevel(mttr),
                restoreTimes.size(), failingSince, averageDuration, daily(points, rangeDays, now));
    }

    private static Instant restores(List<DoraPoint> points, List<Long> restoreTimes) {
        Instant failingSince = null;
        for (DoraPoint point : points.stream().filter(DoraPoint::deployment).sorted(comparing(DoraPoint::time))
                .toList()) {
            if (point.changeFailure()) {
                failingSince = getIfNull(failingSince, point.time());
            } else if (failingSince != null) {
                restoreTimes.add(point.time().getEpochSecond() - failingSince.getEpochSecond());
                failingSince = null;
            }
        }
        return failingSince;
    }

    static DoraLevel deploymentFrequencyLevel(double perWeek) {
        if (perWeek >= 7) {
            return ELITE;
        }
        if (perWeek >= 1) {
            return HIGH;
        }
        if (perWeek >= 7.0 / 30) {
            return MEDIUM;
        }
        return LOW;
    }

    static DoraLevel leadTimeLevel(long seconds) {
        return duration(seconds, DAY, WEEK, MONTH);
    }

    static DoraLevel timeToRestoreLevel(long seconds) {
        return duration(seconds, HOUR, DAY, WEEK);
    }

    static DoraLevel changeFailureRateLevel(double percent) {
        if (percent <= 5) {
            return ELITE;
        }
        if (percent <= 10) {
            return HIGH;
        }
        if (percent <= 15) {
            return MEDIUM;
        }
        return LOW;
    }

    private static DoraLevel duration(long seconds, long elite, long high, long medium) {
        if (seconds < elite) {
            return ELITE;
        }
        if (seconds < high) {
            return HIGH;
        }
        if (seconds < medium) {
            return MEDIUM;
        }
        return LOW;
    }

    private static Long median(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static List<DailyActivity> daily(List<DoraPoint> points, int rangeDays, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, UTC);
        Map<LocalDate, int[]> days = new LinkedHashMap<>();
        for (int i = rangeDays - 1; i >= 0; i--) {
            days.put(today.minusDays(i), new int[3]);
        }
        for (DoraPoint point : points) {
            int[] counts = days.get(LocalDate.ofInstant(point.time(), UTC));
            if (counts != null) {
                counts[0]++;
                if (point.changeFailure()) {
                    counts[1]++;
                }
                if (point.deployment()) {
                    counts[2]++;
                }
            }
        }
        return days.entrySet().stream()
                .map(e -> new DailyActivity(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }
}
