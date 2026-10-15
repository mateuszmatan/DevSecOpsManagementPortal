package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

public record ProductHealth(Product product, int pipelineCount, RunResult overall,
                            Map<RunResult, Integer> statusCounts, Instant lastRunAt) {

    public ProductHealth {
        requireNonNull(product, "the health of a product names the product");
        statusCounts = statusCounts.isEmpty() ? Map.of() : unmodifiableMap(new EnumMap<>(statusCounts));
    }
}
