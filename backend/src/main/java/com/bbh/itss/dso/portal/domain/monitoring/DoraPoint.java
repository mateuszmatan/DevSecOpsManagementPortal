package com.bbh.itss.dso.portal.domain.monitoring;

import java.time.Instant;

public record DoraPoint(Instant time, boolean deployment, boolean changeFailure, long leadTimeSeconds,
                        long durationSeconds) {
}
