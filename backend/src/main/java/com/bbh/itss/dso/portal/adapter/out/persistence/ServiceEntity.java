package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketType;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.catalog.TestJobType;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.EmbeddedColumnNaming;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_SERVICE")
@NoArgsConstructor(access = PROTECTED)
public class ServiceEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Getter(PACKAGE)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID")
    @Getter(PACKAGE)
    private ProductEntity product;

    @Getter(PACKAGE)
    private String name;

    private String description;
    private int displayOrder;
    private SettingsEmbeddable settings;

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_TEST_JOB", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @OrderColumn(name = "POSITION")
    private List<TestJobEmbeddable> testJobs = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<UrbanCodeApplicationEntity> urbanCodeApplications = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_NEXUS_IQ_APP", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @OrderColumn(name = "POSITION")
    private List<NexusIqApplicationEmbeddable> nexusIqApplications = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_SSH_TARGET", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @MapKeyEnumerated(STRING)
    @MapKeyColumn(name = "REGION")
    private Map<Region, SshTargetEmbeddable> sshTargets = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_OPENSHIFT_TARGET", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @MapKeyEnumerated(STRING)
    @MapKeyColumn(name = "REGION")
    private Map<Region, OpenShiftTargetEmbeddable> openShiftTargets = new HashMap<>();

    ServiceEntity(ProductEntity product) {
        this.product = product;
    }

    List<UrbanCodeApplicationSettings> urbanCodeApplications() {
        return urbanCodeApplications.stream().map(UrbanCodeApplicationEntity::toDomain).toList();
    }

    Service toDomain() {
        return new Service(id, name, description, displayOrder, RecordMapper.map(ServiceSettings.class, settings, this));
    }

    void apply(Service service) {
        ServiceSettings source = service.settings();
        name = service.name();
        description = service.description();
        displayOrder = service.displayOrder();
        settings = RecordMapper.map(source, SettingsEmbeddable.class);
        replace(testJobs, source.testJobs().stream().map(job -> RecordMapper.map(job, TestJobEmbeddable.class)).toList());
        replace(nexusIqApplications, source.nexusIqApplications().stream()
                .map(application -> RecordMapper.map(application, NexusIqApplicationEmbeddable.class)).toList());
        replace(sshTargets, regions(source.sshTargets(), SshTargetEmbeddable.class));
        replace(openShiftTargets, regions(source.openShiftTargets(), OpenShiftTargetEmbeddable.class));
        List<UrbanCodeApplicationSettings> applications = source.urbanCodeApplications();
        if (!applications.equals(urbanCodeApplications())) {
            urbanCodeApplications.clear();
            for (int position = 0; position < applications.size(); position++) {
                urbanCodeApplications.add(new UrbanCodeApplicationEntity(this, position, applications.get(position)));
            }
        }
    }

    void releaseName() {
        name = "~" + id;
    }

    private static <T> void replace(Map<Region, T> current, Map<Region, T> replacement) {
        if (!current.equals(replacement)) {
            current.clear();
            current.putAll(replacement);
        }
    }

    private static <S, T> Map<Region, T> regions(Map<Region, S> targets, Class<T> type) {
        Map<Region, T> mapped = new EnumMap<>(Region.class);
        targets.forEach((region, target) -> mapped.put(region, RecordMapper.map(target, type)));
        return mapped;
    }

    @Embeddable
    public record SettingsEmbeddable(
            BuildSettingsEmbeddable build,
            UnitTestSettingsEmbeddable unitTests,
            TestSettingsEmbeddable tests,
            DeploymentSettingsEmbeddable deployment,
            @EmbeddedColumnNaming("DELIVERY_%s") ToolCommandEmbeddable delivery,
            @EmbeddedColumnNaming("UCD_%s") UrbanCodeSettingsEmbeddable urbanCode,
            AppScanSettingsEmbeddable appScan,
            @EmbeddedColumnNaming("SONAR_%s") SonarSettingsEmbeddable sonar,
            NexusIqSettingsEmbeddable nexusIq,
            ScmSettingsEmbeddable scm,
            @EmbeddedColumnNaming("GOLDEN_FIX_%s") GoldenFixPolicyEmbeddable goldenFix,
            MetricsSettingsEmbeddable metrics,
            @EmbeddedColumnNaming("FLUTTER_%s") FlutterSettingsEmbeddable flutter) {
    }

    @Embeddable
    public record BuildSettingsEmbeddable(
            @Enumerated(STRING) @Column(name = "BUILD_TOOL") BuildTool tool,
            String sourceDir, String javaPath,
            @Column(name = "BUILD_TOOL_AUTO_SETUP") Boolean autoSetup,
            String buildPath,
            @EmbeddedColumnNaming("BUILD_%s") ToolCommandEmbeddable command) {
    }

    @Embeddable
    public record UnitTestSettingsEmbeddable(
            @EmbeddedColumnNaming("UNIT_TEST_%s") ToolCommandEmbeddable command,
            @Column(name = "UNIT_TEST_RESULTS") String resultPattern,
            @Column(name = "UNIT_TEST_ROOT_DIR") String rootDir,
            @Column(name = "UNIT_TEST_REPORT_DIR") String reportOutDir,
            @Column(name = "UNIT_TEST_ALLOW_EMPTY") Boolean allowEmptyResults,
            String coverageReportPath) {
    }

    @Embeddable
    public record TestSettingsEmbeddable(@Column(name = "TESTS_MAX_PARALLEL") Integer maxParallel,
            Integer smokeMaxParallel, Integer regressionMaxParallel, Integer performanceMaxParallel,
            Boolean smokeRequired, Boolean regressionRequired, Boolean performanceRequired,
            Integer smokePollIntervalSec, Integer regressionPollIntervalSec, Integer performancePollIntervalSec) {
    }

    @Embeddable
    public record TestJobEmbeddable(
            @Enumerated(STRING) TestStage stage,
            String name,
            @Enumerated(STRING) @Column(name = "JOB_TYPE") TestJobType type,
            String job, Integer timeoutMinutes, String parameters, String remoteJenkins, String remoteJenkinsUrl,
            String credentialsId, Integer pollIntervalSec, String tokenCredentialsId, Boolean abortTriggeredJob,
            Boolean overrideTrustAllCertificates, Boolean preventRemoteBuildQueue, Boolean trustAllCertificates,
            Boolean useCrumbCache, Boolean useJobInfoCache) {
    }

    @Embeddable
    public record DeploymentSettingsEmbeddable(
            @Enumerated(STRING) @Column(name = "DEPLOY_TARGET") DeployTarget target,
            String appName, String artifactName, String baseArtifactName) {
    }

    @Embeddable
    public record ToolCommandEmbeddable(
            @Convert(converter = DelimitedListConverter.Tokens.class) List<String> tasks,
            @Convert(converter = DelimitedListConverter.Tokens.class) List<String> flags,
            String directory, String mavenHome, List<String> environment, String label, Boolean returnStdout) {
    }

    @Embeddable
    public record UrbanCodeSettingsEmbeddable(String siteName, String deployProcess, Boolean skipWait,
            Boolean deployWithSnapshot, Boolean updateSnapshotComponents, Boolean includeOnlyDeployVersions,
            Boolean deployOnlyChanged, String deployDescription, String requestProperties) {
    }

    @Embeddable
    public record UrbanCodeApplicationEmbeddable(String applicationName, @Column(name = "DEPLOY_ORDER") Integer order,
            @Convert(converter = DelimitedListConverter.Commas.class) List<String> environments, String snapshotName,
            String siteName, String deployProcess, Boolean skipWait, Boolean deployWithSnapshot,
            Boolean updateSnapshotComponents, Boolean includeOnlyDeployVersions, Boolean deployOnlyChanged,
            String deployDescription, String description, String requestProperties) {
    }

    @Embeddable
    public record UrbanCodeComponentEmbeddable(String componentName, String baseDir, String fileIncludePatterns,
            String fileExcludePatterns, String versionPrefix, @Column(name = "COMPONENT_VERSION") String version,
            Boolean incrementalVersion, String extensions, String charset, String pushDescription,
            String versionProperties, String versionDescription) {
    }

    @Embeddable
    public record SshTargetEmbeddable(String host, @Column(name = "SSH_USER") String user, String deployDir,
            String deployScript, String versionFile) {
    }

    @Embeddable
    public record OpenShiftTargetEmbeddable(String projectBuild, String buildConfigPath, String dockerFilePath,
            String buildContext, String addFile, String dockerRepoPush, String dockerRepoPull, String certDir,
            String nexusAuthFile, String projectDeployment, String deployConfigPath, String configPath,
            Boolean skipConfigDeploy, String healthCheckUrl, String routeHostname, String deploymentPath,
            String deploymentRepoUrl, String deploymentRepoBranch, String deploymentRepoCredentialsId,
            String buildTag, String internalDockerUrl) {
    }

    @Embeddable
    public record AppScanSettingsEmbeddable(
            @Column(name = "APPSCAN_APP_ID") String applicationId,
            String sastScanName,
            @Column(name = "SAST_INCLUDED_DIRS") List<String> includedDirs,
            @Column(name = "SAST_EXCLUDED_DIRS") List<String> excludedDirs,
            @Column(name = "APPSCAN_COMPILE") Boolean compile,
            @Column(name = "APPSCAN_SOURCE_CODE_ONLY") Boolean sourceCodeOnly,
            @Column(name = "APPSCAN_USE_CONFIG_FILE") Boolean useConfigFile,
            @Column(name = "APPSCAN_INSECURE_TLS") Boolean insecureTls,
            @Column(name = "APPSCAN_CLIENT_PATH") String clientPath,
            @EmbeddedColumnNaming("APPSCAN_COMPILE_%s") ToolCommandEmbeddable compileCommand,
            Boolean dastEnabled, String dastScanName, String dastTargetUrl, String dastPresenceId,
            @Column(name = "APPSCAN_SECRET_CREDENTIALS_ID") String secretCredentialsId) {
    }

    @Embeddable
    public record SonarSettingsEmbeddable(String projectName, String projectKey, String installationName,
            String credentialsId, String authTokenCredentialsId, String badgeToken, Boolean addBadges,
            Boolean fullBadges, ToolCommandEmbeddable command, String serverUrl) {
    }

    @Embeddable
    public record NexusIqSettingsEmbeddable(
            @Column(name = "NEXUS_IQ_SERVER_URL") String serverUrl,
            @Column(name = "NEXUS_IQ_CREDENTIALS_ID") String credentialsId,
            String scaScanName) {
    }

    @Embeddable
    public record NexusIqApplicationEmbeddable(String application, List<String> scanPatterns, String stage,
            Boolean failOnNetworkError) {
    }

    @Embeddable
    public record ScmSettingsEmbeddable(
            String repositoryUrl,
            @Column(name = "BITBUCKET_CREDENTIALS_ID") String credentialsId,
            @Enumerated(STRING) @Column(name = "BITBUCKET_AUTH_TYPE") BitbucketAuthType authType,
            @Enumerated(STRING) @Column(name = "BITBUCKET_TYPE") BitbucketType type,
            @Column(name = "BITBUCKET_TARGET_BRANCH") String targetBranch,
            @Column(name = "BITBUCKET_CLONE_URL") String cloneUrl,
            @Convert(converter = DelimitedListConverter.Commas.class)
            @Column(name = "BITBUCKET_REVIEWERS") List<String> reviewers,
            @Column(name = "BITBUCKET_API_URL") String apiUrl,
            @Column(name = "BITBUCKET_WORKSPACE") String workspace,
            @Column(name = "BITBUCKET_PROJECT_KEY") String projectKey,
            @Column(name = "BITBUCKET_REPO_SLUG") String repoSlug) {
    }

    @Embeddable
    public record GoldenFixPolicyEmbeddable(
            Boolean enabled,
            @Column(name = "DIRECT_ONLY") Boolean onlyDirectDependencies,
            Integer minThreatLevel,
            @Convert(converter = DelimitedListConverter.Commas.class) List<String> ecosystems,
            @Column(name = "VERSION_TYPES") List<String> goldenVersionTypes,
            List<String> excludeDirs,
            @Column(name = "VERIFY") Boolean verifyEnabled,
            @Column(name = "VERIFY_ATTEMPTS") Integer verifyMaxAttempts,
            @Column(name = "VERIFY_TIMEOUT") Integer verifyTimeoutMinutes,
            @Column(name = "VERIFY_MAVEN") String verifyMavenCommand,
            @Column(name = "VERIFY_GRADLE") String verifyGradleCommand,
            @Column(name = "VERIFY_NPM") String verifyNpmCommand,
            @Column(name = "VERIFY_PIP") String verifyPipCommand,
            @Column(name = "VERIFY_PUB") String verifyPubCommand,
            @Column(name = "AUTHOR_NAME") String commitAuthorName,
            @Column(name = "AUTHOR_EMAIL") String commitAuthorEmail,
            String timeZone) {
    }

    @Embeddable
    public record MetricsSettingsEmbeddable(@Column(name = "METRICS_ENABLED") Boolean enabled, String influxProject,
            String influxEnv, String influxUrl, String influxCredentialsId) {
    }

    @Embeddable
    public record FlutterSettingsEmbeddable(@Enumerated(STRING) FlutterPlatform platform,
            List<String> modules, List<String> testModules, List<String> testSubmodules, List<String> testSubplugins,
            @Column(name = "SIGNING_CREDENTIALS_ID") String signingPasswordCredentialsId,
            String prodLicenseCredentialsId, String testLicenseCredentialsId, String deliveryGroup,
            String deliveryArtifact, String deliveryPlugin, String sonarSources, String sonarTests,
            @Column(name = "SONAR_PLUGIN") Boolean sonarFlutterPlugin, String dartAnalyzeCommand,
            String sonarScannerVersion) {
    }
}
