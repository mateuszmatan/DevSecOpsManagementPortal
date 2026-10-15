package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary;

public record PortfolioActivity(int pipelines, DoraSummary dora, String metricsError) {
}
