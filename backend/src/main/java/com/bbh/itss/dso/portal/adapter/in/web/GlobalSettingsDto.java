package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
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
        @NotNull @Valid ServiceDto.GoldenFixPolicyDto goldenFix) {

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
            @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE) String proxyHost,
            @Min(1) @Max(65535) Integer proxyPort,
            @Size(max = 100) String proxyUser,
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
            @Size(max = 255) String iosBuildAgent) implements Mirrors<PlatformSettings> {
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
            @NotNull @Min(1) @Max(1440) Integer sonarQualityGateTimeoutMinutes) implements Mirrors<ScanSettings> {
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
            @NotNull @Min(1) Integer testsMaxParallel) implements Mirrors<ServiceDefaults> {
    }
}
