package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.domain.monitoring.DashboardLink;

import java.util.List;

public record MonitoringStatus(boolean metricsConfigured, boolean metricsReachable, String metricsError,
                               List<DashboardLink> dashboards) {

    public MonitoringStatus {
        dashboards = List.copyOf(dashboards);
    }
}
