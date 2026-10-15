package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.adapter.out.persistence.ServiceEntity.GoldenFixPolicyEmbeddable;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.Scanner;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.EmbeddedColumnNaming;
import org.springframework.data.domain.Persistable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.EAGER;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_GLOBAL_SETTINGS")
@NoArgsConstructor(access = PROTECTED)
public class GlobalSettingsEntity extends AuditedEntity implements Persistable<Long> {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    private ValuesEmbeddable values;

    @ElementCollection(fetch = EAGER)
    @CollectionTable(name = "DSO_GLOBAL_SEVERITY_LIMIT", joinColumns = @JoinColumn(name = "SETTINGS_ID"))
    @MapKeyEnumerated(STRING)
    @MapKeyColumn(name = "SCANNER")
    private Map<Scanner, SeverityLimitsEmbeddable> limits = new HashMap<>();

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return createdAt() == null;
    }

    GlobalSettings toDomain() {
        return new GlobalSettings(RecordMapper.map(GlobalSettingsValues.class, values, this), version(), updatedAt());
    }

    void apply(GlobalSettingsValues source) {
        values = RecordMapper.map(source, ValuesEmbeddable.class);
        Map<Scanner, SeverityLimitsEmbeddable> replacement = new EnumMap<>(Scanner.class);
        source.limits().forEach((scanner, limit) -> replacement.put(scanner,
                RecordMapper.map(limit, SeverityLimitsEmbeddable.class)));
        if (!limits.equals(replacement)) {
            limits.clear();
            limits.putAll(replacement);
        }
    }

    @Embeddable
    public record ValuesEmbeddable(
            PlatformSettingsEmbeddable platform,
            DeploymentDefaultsEmbeddable deployment,
            ScanSettingsEmbeddable scans,
            @EmbeddedColumnNaming("RELEASE_GATE_%s") ReleaseGateSettingsEmbeddable releaseGate,
            ServiceDefaultsEmbeddable serviceDefaults,
            @EmbeddedColumnNaming("GOLDEN_FIX_%s") GoldenFixPolicyEmbeddable goldenFix) {
    }

    @Embeddable
    public record PlatformSettingsEmbeddable(String jenkinsUrl, String jenkinsLibrary, String asocUrl,
            @Column(name = "APPSCAN_CLIENT_LINUX_URL") String appScanClientLinuxUrl,
            @Column(name = "APPSCAN_CLIENT_WINDOWS_URL") String appScanClientWindowsUrl,
            String proxyHost, Integer proxyPort, String proxyUser, String oisHost, String sonarServerUrl,
            String sonarInstallationName, String nexusIqServerUrl, String nexusIqCredentialsId,
            @Column(name = "NEXUS_SNAPSHOT_URL") String nexusSnapshotRepositoryUrl, String nexusSnapshotRepositoryId,
            String influxWriteUrl, String influxCredentialsId, String iosBuildAgent) {
    }

    @Embeddable
    public record DeploymentDefaultsEmbeddable(@Column(name = "UCD_SITE_NAME") String urbanCodeSiteName,
            @Column(name = "UCD_DEPLOY_PROCESS") String urbanCodeDeployProcess, String rdHost, String qcHost,
            String sshUser, String deployScript, String versionFile) {
    }

    @Embeddable
    public record SeverityLimitsEmbeddable(Integer maxCritical, Integer maxHigh, Integer maxMedium) {
    }

    @Embeddable
    public record ScanSettingsEmbeddable(
            Integer coverageMinLine,
            @Column(name = "SAST_PREPARE_TIMEOUT_MIN") Integer sastPrepareTimeoutMinutes,
            @Column(name = "SAST_POLL_TIMEOUT_MIN") Integer sastPollTimeoutMinutes,
            @Column(name = "SAST_POLL_INTERVAL_SEC") Integer sastPollIntervalSeconds,
            @Column(name = "DAST_POLL_TIMEOUT_MIN") Integer dastPollTimeoutMinutes,
            @Column(name = "DAST_POLL_INTERVAL_SEC") Integer dastPollIntervalSeconds,
            @Column(name = "DAST_REPORT_TIMEOUT_MIN") Integer dastReportTimeoutMinutes,
            @Column(name = "DAST_REPORT_INTERVAL_SEC") Integer dastReportIntervalSeconds,
            Boolean sonarWaitForQualityGate,
            @Column(name = "SONAR_QUALITY_GATE_TIMEOUT_MIN") Integer sonarQualityGateTimeoutMinutes) {
    }

    @Embeddable
    public record ReleaseGateSettingsEmbeddable(List<Scanner> scanners, Boolean requireCoverage, String stateFile) {
    }

    @Embeddable
    public record ServiceDefaultsEmbeddable(
            @Enumerated(STRING) @Column(name = "DEFAULT_BUILD_TOOL") BuildTool buildTool,
            @Enumerated(STRING) @Column(name = "DEFAULT_DEPLOY_TARGET") DeployTarget deployTarget,
            @Column(name = "DEFAULT_SOURCE_DIR") String sourceDir,
            Integer testsMaxParallel) {
    }
}
