package com.bbh.itss.dso.portal.adapter.out.grafana;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.anyNotNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@ConfigurationProperties("dso.grafana")
public record GrafanaProperties(List<Instance> instances) {

    public GrafanaProperties {
        instances = emptyIfNull(instances).stream().filter(Instance::configured).toList();
    }

    public record Instance(String name, String dashboardUrl, String securityDashboardUrl) {

        public Instance {
            name = defaultIfBlank(trim(name), "Grafana");
            dashboardUrl = trimToNull(dashboardUrl);
            securityDashboardUrl = trimToNull(securityDashboardUrl);
        }

        boolean configured() {
            return anyNotNull(dashboardUrl, securityDashboardUrl);
        }
    }
}
