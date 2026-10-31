package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.settings.DatabaseDefaults;
import com.bbh.itss.dso.portal.domain.settings.DeploymentDefaults;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import com.bbh.itss.dso.portal.domain.settings.ReleaseGateSettings;
import com.bbh.itss.dso.portal.domain.settings.ScanSettings;
import com.bbh.itss.dso.portal.domain.settings.Scanner;
import com.bbh.itss.dso.portal.domain.settings.ServiceDefaults;
import com.bbh.itss.dso.portal.domain.settings.SeverityLimits;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.HOST;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.HOST_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE_URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE_URL_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ABSOLUTE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ABSOLUTE_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ARCHIVE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ARCHIVE_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.JAR;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.JAR_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.MAVEN_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.OPTIONAL;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.REPOSITORY_URL;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.REPOSITORY_URL_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.SHA256;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.SHA256_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.TARGET;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.TARGET_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.TOOL_URL;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.TOOL_URL_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ZIP;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ZIP_MESSAGE;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

@JsonIgnoreProperties(value = "updatedAt", allowGetters = true)
public record GlobalSettingsDto(
        Long version,
        Instant updatedAt,
        @NotNull @Valid PlatformSettingsDto platform,
        @NotNull @Valid DeploymentDefaultsDto deployment,
        @NotNull Map<@NotNull Scanner, @NotNull @Valid SeverityLimitsDto> limits,
        @NotNull @Valid ScanSettingsDto scans,
        @NotNull @Valid ReleaseGateSettingsDto releaseGate,
        @NotNull @Valid ServiceDefaultsDto serviceDefaults,
        @NotNull @Valid ServiceDto.GoldenFixPolicyDto goldenFix,
        @Valid DatabaseDefaultsDto database) {

    static GlobalSettingsDto from(GlobalSettings settings) {
        return RecordMapper.map(GlobalSettingsDto.class, settings, settings.values());
    }

    GlobalSettingsValues toValues() {
        return RecordMapper.map(this, GlobalSettingsValues.class);
    }

    public record PlatformSettingsDto(
            @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE) String jenkinsUrl,
            @NotBlank @Size(max = 200) String jenkinsLibrary,
            @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE) String asocUrl,
            @NotBlank @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String appScanClientLinuxUrl,
            @NotBlank @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String appScanClientWindowsUrl,
            @NotBlank @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE) String proxyHost,
            @NotNull @Min(1) @Max(65535) Integer proxyPort,
            @NotBlank @Size(max = 100) String proxyUser,
            @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE) String oisHost,
            @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE) String sonarServerUrl,
            @NotBlank @Size(max = 200) String sonarInstallationName,
            @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE) String nexusIqServerUrl,
            @NotBlank @Size(max = 200) String nexusIqCredentialsId,
            @Size(max = 1000) @Pattern(regexp = SHELL_SAFE_URL, message = SHELL_SAFE_URL_MESSAGE)
            String nexusSnapshotRepositoryUrl,
            @Size(max = 200) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String nexusSnapshotRepositoryId,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String influxWriteUrl,
            @Size(max = 200) String influxCredentialsId,
            @Size(max = 255) String iosBuildAgent,
            @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE) String influxUrl,
            @Size(max = 200) String influxOrg,
            @Size(max = 200) String influxBucket,
            @JsonProperty(access = WRITE_ONLY) @Size(max = 500) String influxToken,
            @Size(max = 100) String grafanaName,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String grafanaDashboardUrl,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String grafanaSecurityDashboardUrl,
            @Size(max = 100) String grafana2Name,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String grafana2DashboardUrl,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String grafana2SecurityDashboardUrl)
            implements Mirrors<PlatformSettings> {
    }

    public record DeploymentDefaultsDto(
            @NotBlank @Size(max = 200) String urbanCodeSiteName,
            @NotBlank @Size(max = 200) String urbanCodeDeployProcess,
            @NotBlank @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE) String rdHost,
            @NotBlank @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE) String qcHost,
            @NotBlank @Size(max = 100) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String sshUser,
            @NotBlank @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String deployScript,
            @NotBlank @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE)
            String versionFile) implements Mirrors<DeploymentDefaults> {
    }

    public record SeverityLimitsDto(
            @NotNull @Min(0) @Max(100_000) Integer maxCritical,
            @NotNull @Min(0) @Max(100_000) Integer maxHigh,
            @NotNull @Min(0) @Max(100_000) Integer maxMedium) implements Mirrors<SeverityLimits> {
    }

    public record ScanSettingsDto(
            @NotNull @Min(0) @Max(100) Integer coverageMinLine,
            @NotNull @Min(1) @Max(1440) Integer sastPrepareTimeoutMinutes,
            @NotNull @Min(1) @Max(1440) Integer sastPollTimeoutMinutes,
            @NotNull @Min(1) @Max(3600) Integer sastPollIntervalSeconds,
            @NotNull @Min(1) @Max(1440) Integer dastPollTimeoutMinutes,
            @NotNull @Min(1) @Max(3600) Integer dastPollIntervalSeconds,
            @NotNull @Min(1) @Max(1440) Integer dastReportTimeoutMinutes,
            @NotNull @Min(1) @Max(3600) Integer dastReportIntervalSeconds,
            @NotNull Boolean sonarWaitForQualityGate,
            @NotNull @Min(1) @Max(1440) Integer sonarQualityGateTimeoutMinutes,
            @NotNull Boolean parallelStaticAnalysis) implements Mirrors<ScanSettings> {
    }

    public record ReleaseGateSettingsDto(
            @NotNull @Size(max = 4) List<@NotNull Scanner> scanners,
            @NotNull Boolean requireCoverage,
            String stateFile) implements Mirrors<ReleaseGateSettings> {
    }

    public record ServiceDefaultsDto(
            @NotNull BuildTool buildTool,
            @NotNull DeployTarget deployTarget,
            @Size(max = 500) String sourceDir,
            @NotNull @Min(1) Integer testsMaxParallel,
            @Size(max = 200) String nodeInstallationName,
            @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE) String npmRegistry,
            @Size(max = 200) String npmrcCredentialsId) implements Mirrors<ServiceDefaults> {
    }

    public record DatabaseDefaultsDto(
            @Pattern(regexp = OPTIONAL + ABSOLUTE, message = ABSOLUTE_MESSAGE) String liquibaseHome,
            @Size(max = 1000) @Pattern(regexp = OPTIONAL + TOOL_URL, message = TOOL_URL_MESSAGE)
            @Pattern(regexp = OPTIONAL + ARCHIVE, message = ARCHIVE_MESSAGE) String liquibaseUrl,
            @Pattern(regexp = OPTIONAL + SHA256, message = SHA256_MESSAGE) String liquibaseSha256,
            @Min(17) @Max(25) Integer liquibaseJavaVersion,
            @Pattern(regexp = OPTIONAL + ABSOLUTE, message = ABSOLUTE_MESSAGE)
            @Pattern(regexp = OPTIONAL + JAR, message = JAR_MESSAGE) String oracleJdbcPath,
            @Size(max = 1000) @Pattern(regexp = OPTIONAL + TOOL_URL, message = TOOL_URL_MESSAGE)
            @Pattern(regexp = OPTIONAL + JAR, message = JAR_MESSAGE) String oracleJdbcUrl,
            @Pattern(regexp = OPTIONAL + SHA256, message = SHA256_MESSAGE) String oracleJdbcSha256,
            @Pattern(regexp = OPTIONAL + ABSOLUTE, message = ABSOLUTE_MESSAGE) String utplsqlHome,
            @Size(max = 1000) @Pattern(regexp = OPTIONAL + TOOL_URL, message = TOOL_URL_MESSAGE)
            @Pattern(regexp = OPTIONAL + ZIP, message = ZIP_MESSAGE) String utplsqlUrl,
            @Pattern(regexp = OPTIONAL + SHA256, message = SHA256_MESSAGE) String utplsqlSha256,
            @Size(max = 200) List<@NotBlank @Pattern(regexp = TARGET, message = TARGET_MESSAGE) String> allowedTargets,
            @Pattern(regexp = OPTIONAL + MAVEN, message = MAVEN_MESSAGE) String snapshotRepositoryId,
            @Size(max = 1000) @Pattern(regexp = OPTIONAL + REPOSITORY_URL, message = REPOSITORY_URL_MESSAGE)
            String snapshotRepositoryUrl,
            @Pattern(regexp = OPTIONAL + MAVEN, message = MAVEN_MESSAGE) String releaseRepositoryId,
            @Size(max = 1000) @Pattern(regexp = OPTIONAL + REPOSITORY_URL, message = REPOSITORY_URL_MESSAGE)
            String releaseRepositoryUrl) implements Mirrors<DatabaseDefaults> {
    }
}
