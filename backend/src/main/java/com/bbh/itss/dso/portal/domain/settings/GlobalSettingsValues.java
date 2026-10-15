package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.With;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.catalog.BuildSettings.DEFAULT_SOURCE_DIR;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.dso.portal.domain.settings.ReleaseGateSettings.STATE_FILE;
import static com.bbh.itss.dso.portal.domain.settings.SeverityLimits.ZERO;
import static java.util.Collections.unmodifiableMap;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;

public record GlobalSettingsValues(@With PlatformSettings platform, DeploymentDefaults deployment,
                                   Map<Scanner, SeverityLimits> limits, ScanSettings scans,
                                   ReleaseGateSettings releaseGate, ServiceDefaults serviceDefaults,
                                   GoldenFixPolicy goldenFix) {

    public GlobalSettingsValues {
        Map<Scanner, SeverityLimits> ordered = new EnumMap<>(Scanner.class);
        emptyIfNull(limits).forEach((scanner, value) -> {
            if (allNotNull(scanner, value)) {
                ordered.put(scanner, value);
            }
        });
        limits = unmodifiableMap(ordered);
        goldenFix = goldenFix == null ? null : goldenFix.enabledByDefault();
    }

    public static GlobalSettingsValues bbhDefaults() {
        PlatformSettings platform = PlatformSettings.builder().jenkinsLibrary("DevSecOpsJenkinsLibrary")
                .asocUrl("https://bbh.cloud.appscan.com")
                .appScanClientLinuxUrl("https://tools.bbh.com/nexus/repository/releases/com/bbh/appscan/SAClientUtil/"
                        + "8.0.1646_Linux/SAClientUtil-8.0.1646_Linux-SAClientUtil_8.0.1646_Linux.zip")
                .appScanClientWindowsUrl("https://tools.bbh.com/nexus/repository/releases/com/bbh/appscan/SAClientUtil/"
                        + "8.0.1646_Win/SAClientUtil-8.0.1646_Win-SAClientUtil_8.0.1646_Win.zip")
                .proxyHost("tstproxy.bbh.com").proxyPort(9090).proxyUser("PROXY_ASOCJenk").oisHost("oisapi.bbh.com")
                .sonarServerUrl("https://tools.bbh.com/sonar").sonarInstallationName("SonarQube")
                .nexusIqServerUrl("https://tools.bbh.com/IQ").nexusIqCredentialsId("nexusiqP")
                .nexusSnapshotRepositoryUrl("http://tools.bbh.com/nexus/content/repositories/snapshots/")
                .nexusSnapshotRepositoryId("bbh-snapshots")
                .influxWriteUrl("http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics"
                        + "&precision=s")
                .influxCredentialsId("influxdb-token").iosBuildAgent("mac002.bbh.com").build();
        DeploymentDefaults deployment = DeploymentDefaults.builder().urbanCodeSiteName("deploy.bbh.com")
                .urbanCodeDeployProcess("tomcat-app-process").rdHost("rdltaapps1.testbbh.com")
                .qcHost("qcltaapps1.testbbh.com").sshUser("taadmin")
                .deployScript("scripts/deployment/zero-downtime-deployment.sh")
                .versionFile("scripts/deployment/version.properties").build();
        Map<Scanner, SeverityLimits> limits = new EnumMap<>(Scanner.class);
        for (Scanner scanner : Scanner.values()) {
            limits.put(scanner, ZERO);
        }
        ScanSettings scans = ScanSettings.builder().coverageMinLine(60).sastPrepareTimeoutMinutes(120)
                .sastPollTimeoutMinutes(50).sastPollIntervalSeconds(30).dastPollTimeoutMinutes(60)
                .dastPollIntervalSeconds(60).dastReportTimeoutMinutes(30).dastReportIntervalSeconds(30)
                .sonarWaitForQualityGate(true).sonarQualityGateTimeoutMinutes(5).build();
        ReleaseGateSettings releaseGate = new ReleaseGateSettings(List.of(Scanner.values()), true, STATE_FILE);
        ServiceDefaults serviceDefaults = new ServiceDefaults(GRADLE, VM, DEFAULT_SOURCE_DIR, 20);
        GoldenFixPolicy goldenFix = GoldenFixPolicy.builder().enabled(true).onlyDirectDependencies(true)
                .minThreatLevel(2).ecosystems(List.of("maven", "npm", "pypi"))
                .goldenVersionTypes(List.of("recommended-non-breaking-with-dependencies", "recommended-non-breaking"))
                .excludeDirs(List.of()).verifyEnabled(true).verifyMaxAttempts(3).verifyTimeoutMinutes(20)
                .commitAuthorName("DevSecOps GoldenFix").commitAuthorEmail("devsecops-goldenfix@noreply.local").build();
        return new GlobalSettingsValues(platform, deployment, limits, scans, releaseGate, serviceDefaults, goldenFix);
    }

    public void validate(ValidationProblems problems) {
        platform.validate(problems.at("platform"));
        for (Scanner scanner : Scanner.values()) {
            if (!limits.containsKey(scanner)) {
                problems.add("limits." + scanner.name(), "set the limits of every scanner");
            }
        }
        scans.validate(problems.at("scans"));
        releaseGate.validate(problems.at("releaseGate"));
        goldenFix.validateComplete(problems.at("goldenFix"));
    }

    public Map<String, Object> defaultsConfig() {
        ConfigTree defaults = new ConfigTree();
        serviceDefaults.writeTo(defaults);
        scans.writeTo(defaults);
        limits.forEach((scanner, value) -> value.writeTo(defaults, scanner.defaultsPath()));
        releaseGate.writeTo(defaults);
        goldenFix.writeTo(defaults);
        return defaults.toMap(List.of("buildTool", "deployTarget", "sourceDir", "coverage", "tools", "sast",
                "sca", "dast", "tests", "releaseGate", "goldenFix"));
    }
}
