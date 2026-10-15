package com.bbh.itss.dso.portal.domain.monitoring;

import java.io.UncheckedIOException;
import java.util.function.Supplier;

public record MetricsReading<T>(T value, String error) {

    public static <T> MetricsReading<T> of(Supplier<T> query, T fallback) {
        try {
            return new MetricsReading<>(query.get(), null);
        } catch (UncheckedIOException e) {
            return new MetricsReading<>(fallback, e.getMessage());
        }
    }

    public boolean failed() {
        return error != null;
    }
}
