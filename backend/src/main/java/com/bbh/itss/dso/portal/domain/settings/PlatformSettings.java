package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.With;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record PlatformSettings(@With String jenkinsUrl, String jenkinsLibrary, String asocUrl,
                               String appScanClientLinuxUrl, String appScanClientWindowsUrl, String proxyHost,
                               Integer proxyPort, String proxyUser, String oisHost, String sonarServerUrl,
                               String sonarInstallationName, String nexusIqServerUrl, String nexusIqCredentialsId,
                               String nexusSnapshotRepositoryUrl, String nexusSnapshotRepositoryId,
                               String influxWriteUrl, String influxCredentialsId, String iosBuildAgent) {

    public PlatformSettings {
        jenkinsUrl = trimToNull(jenkinsUrl);
        jenkinsLibrary = trimToNull(jenkinsLibrary);
        asocUrl = trimToNull(asocUrl);
        appScanClientLinuxUrl = trimToNull(appScanClientLinuxUrl);
        appScanClientWindowsUrl = trimToNull(appScanClientWindowsUrl);
        proxyHost = trimToNull(proxyHost);
        proxyUser = trimToNull(proxyUser);
        oisHost = trimToNull(oisHost);
        sonarServerUrl = trimToNull(sonarServerUrl);
        sonarInstallationName = trimToNull(sonarInstallationName);
        nexusIqServerUrl = trimToNull(nexusIqServerUrl);
        nexusIqCredentialsId = trimToNull(nexusIqCredentialsId);
        nexusSnapshotRepositoryUrl = trimToNull(nexusSnapshotRepositoryUrl);
        nexusSnapshotRepositoryId = trimToNull(nexusSnapshotRepositoryId);
        influxWriteUrl = trimToNull(influxWriteUrl);
        influxCredentialsId = trimToNull(influxCredentialsId);
        iosBuildAgent = trimToNull(iosBuildAgent);
    }

    public Map<String, Object> toConfig() {
        Map<String, Object> environment = new ConfigTree().set("APPSCAN_SERVER_URL", asocUrl)
                .set("APPSCAN_HOST", host(asocUrl)).set("SA_LINUX_URL", appScanClientLinuxUrl)
                .set("SA_WIN_URL", appScanClientWindowsUrl).set("PROXY_HOST", proxyHost)
                .set("PROXY_PORT", Objects.toString(proxyPort, null)).set("PROXY_USER", proxyUser)
                .toMap();
        return new ConfigTree()
                .set("jenkinsUrl", jenkinsUrl)
                .set("jenkinsLibrary", jenkinsLibrary)
                .set("environment", environment)
                .set("oisHost", oisHost)
                .set("nexusSnapshotRepositoryUrl", nexusSnapshotRepositoryUrl)
                .set("nexusSnapshotRepositoryId", nexusSnapshotRepositoryId)
                .set("iosBuildAgent", iosBuildAgent)
                .toMap();
    }

    public void writeProjectDefaults(ConfigTree config) {
        config.set("asoc.url", asocUrl)
                .set("influx.url", influxWriteUrl)
                .set("influx.credentialsId", influxCredentialsId)
                .set("tools.sonar.serverUrl", sonarServerUrl)
                .set("tools.sonar.installationName", sonarInstallationName)
                .set("tools.nexusIq.serverUrl", nexusIqServerUrl)
                .set("tools.nexusIq.credentialsId", nexusIqCredentialsId);
    }

    public void validate(ValidationProblems problems) {
        if (proxyHost != null && proxyPort == null) {
            problems.add("proxyPort", "is required with a proxy host");
        }
        if (proxyHost == null && proxyPort != null) {
            problems.add("proxyHost", "is required with a proxy port");
        }
    }

    private static String host(String url) {
        try {
            return url == null ? null : URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
