package com.bbh.itss.dso.portal.adapter.out.grafana;

import com.bbh.itss.dso.portal.adapter.MonitoringSettingsStore;
import com.bbh.itss.dso.portal.adapter.out.grafana.GrafanaProperties.Instance;
import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort;
import com.bbh.itss.dso.portal.domain.monitoring.DashboardLink;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.springframework.web.util.UriUtils.encodeQueryParam;

@Component
class GrafanaDashboardLinksAdapter implements DashboardLinksPort {

    private static final Set<PipelineType> SECURITY_DASHBOARD = Set.of(SECURITY, SAST, NEXUS_IQ);

    private final GrafanaProperties grafana;
    private final MonitoringSettingsStore monitoring;

    GrafanaDashboardLinksAdapter(GrafanaProperties grafana) {
        this(grafana, new MonitoringSettingsStore());
    }

    @Autowired
    GrafanaDashboardLinksAdapter(GrafanaProperties grafana, MonitoringSettingsStore monitoring) {
        this.grafana = grafana;
        this.monitoring = monitoring != null ? monitoring : new MonitoringSettingsStore();
    }

    @Override
    public List<DashboardLink> instances() {
        return configuredInstances().stream()
                .map(instance -> new DashboardLink(instance.name(),
                        getIfNull(instance.dashboardUrl(), instance.securityDashboardUrl())))
                .toList();
    }

    @Override
    public List<DashboardLink> dashboards(MetricsTag tag, PipelineType type, int rangeDays) {
        return configuredInstances().stream()
                .flatMap(instance -> dashboard(instance, type).stream()
                        .map(link -> new DashboardLink(instance.name(), link + (link.contains("?") ? "&" : "?")
                                + "var-project=" + encodeQueryParam(tag.project(), UTF_8)
                                + "&from=now-" + rangeDays + "d&to=now")))
                .toList();
    }

    private List<Instance> configuredInstances() {
        List<Instance> fromSettings = monitoring.platform()
                .map(GrafanaDashboardLinksAdapter::fromPlatform)
                .orElse(List.of());
        return fromSettings.isEmpty() ? grafana.instances() : fromSettings;
    }

    private static List<Instance> fromPlatform(PlatformSettings platform) {
        return Stream.of(
                        new Instance(platform.grafanaName(), platform.grafanaDashboardUrl(),
                                platform.grafanaSecurityDashboardUrl()),
                        new Instance(platform.grafana2Name(), platform.grafana2DashboardUrl(),
                                platform.grafana2SecurityDashboardUrl()))
                .filter(Instance::configured)
                .toList();
    }

    private static Optional<String> dashboard(Instance instance, PipelineType type) {
        return Optional.ofNullable(SECURITY_DASHBOARD.contains(type)
                ? getIfNull(instance.securityDashboardUrl(), instance.dashboardUrl()) : instance.dashboardUrl());
    }
}

