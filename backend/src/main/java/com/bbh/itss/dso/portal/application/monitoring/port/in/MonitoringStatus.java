package com.bbh.itss.dso.portal.application.monitoring.port.in;

public record MonitoringStatus(boolean metricsConfigured, boolean metricsReachable, String metricsError) {
}
