package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketType;
import com.bbh.itss.dso.portal.domain.catalog.BuildSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings;
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform;
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings;
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy;
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication;
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings;
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings;
import com.bbh.itss.dso.portal.domain.catalog.SshTarget;
import com.bbh.itss.dso.portal.domain.catalog.TestJob;
import com.bbh.itss.dso.portal.domain.catalog.TestJobType;
import com.bbh.itss.dso.portal.domain.catalog.TestSettings;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.catalog.ToolCommand;
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.FOLDER;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.FOLDER_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.GIT_URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.IMAGE_TAG;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.IMAGE_TAG_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.MODULE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.MODULE_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.NO_WHITESPACE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.NO_WHITESPACE_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.POWERSHELL_PATH;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.POWERSHELL_PATH_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE;

@Builder
public record ServiceDto(
        Long id,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$",
                message = "use letters, digits, '.', '-' or '_', starting with a letter or digit")
        String name,
        @Size(max = ServiceDraft.DESCRIPTION_MAX) String description,
        @NotNull @Valid BuildSettingsDto build,
        @Valid UnitTestSettingsDto unitTests,
        @Valid TestSettingsDto tests,
        @Size(max = 1000) List<@NotNull @Valid TestJobDto> testJobs,
        @NotNull @Valid DeploymentSettingsDto deployment,
        @Valid ToolCommandDto delivery,
        @Valid UrbanCodeSettingsDto urbanCode,
        @Size(max = 20) List<@NotNull @Valid UrbanCodeApplicationSettingsDto> urbanCodeApplications,
        Map<Region, @Valid SshTargetDto> sshTargets,
        Map<Region, @Valid OpenShiftTargetDto> openShiftTargets,
        @NotNull @Valid AppScanSettingsDto appScan,
        @Valid SonarSettingsDto sonar,
        @Valid NexusIqSettingsDto nexusIq,
        @Size(max = 20) List<@NotNull @Valid NexusIqApplicationDto> nexusIqApplications,
        @Valid ScmSettingsDto scm,
        @Valid GoldenFixPolicyDto goldenFix,
        @Valid MetricsSettingsDto metrics,
        @Valid FlutterSettingsDto flutter) {

    static ServiceDto from(Service service) {
        return RecordMapper.map(ServiceDto.class, service, service.settings());
    }

    ServiceDraft toDraft() {
        return new ServiceDraft(id, name, description, RecordMapper.map(this, ServiceSettings.class));
    }

    public record BuildSettingsDto(
            @NotNull BuildTool tool,
            @Size(max = 500) String sourceDir,
            @Size(max = 500) String javaPath,
            Boolean autoSetup,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String buildPath,
            @Valid ToolCommandDto command) implements Mirrors<BuildSettings> {
    }

    public record UnitTestSettingsDto(
            @Valid ToolCommandDto command,
            @Size(max = 500) String resultPattern,
            @Size(max = 500) String rootDir,
            @Size(max = 500) String reportOutDir,
            Boolean allowEmptyResults,
            @Size(max = 500) String coverageReportPath) implements Mirrors<UnitTestSettings> {
    }

    public record TestSettingsDto(
            @Min(1) Integer maxParallel,
            @Min(1) Integer smokeMaxParallel,
            @Min(1) Integer regressionMaxParallel,
            @Min(1) Integer performanceMaxParallel,
            Boolean smokeRequired,
            Boolean regressionRequired,
            Boolean performanceRequired,
            @Min(1) Integer smokePollIntervalSec,
            @Min(1) Integer regressionPollIntervalSec,
            @Min(1) Integer performancePollIntervalSec) implements Mirrors<TestSettings> {
    }

    public record TestJobDto(
            @NotNull TestStage stage,
            @Size(max = 200) String name,
            TestJobType type,
            @NotBlank @Size(max = 1000) String job,
            @Min(1) Integer timeoutMinutes,
            @Size(max = 2000) String parameters,
            @Size(max = 200) String remoteJenkins,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String remoteJenkinsUrl,
            @Size(max = 200) String credentialsId,
            @Min(1) Integer pollIntervalSec,
            @Size(max = 200) String tokenCredentialsId,
            Boolean abortTriggeredJob,
            Boolean overrideTrustAllCertificates,
            Boolean preventRemoteBuildQueue,
            Boolean trustAllCertificates,
            Boolean useCrumbCache,
            Boolean useJobInfoCache) implements Mirrors<TestJob> {
    }

    public record DeploymentSettingsDto(
            @NotNull DeployTarget target,
            @Size(max = 200) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String appName,
            @Size(max = 300) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String artifactName,
            @Size(max = 300) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE)
            String baseArtifactName) implements Mirrors<DeploymentSettings> {
    }

    @Builder
    public record ToolCommandDto(
            @Size(max = 30) List<@NotBlank @Size(max = 200) String> tasks,
            @Size(max = 40) List<@NotBlank @Size(max = 300) String> flags,
            @Size(max = 500) String directory,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String mavenHome,
            @Size(max = 30)
            List<@Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_+]*=.*$", message = "write each variable as NAME=value")
                    @Size(max = 500) String> environment,
            @Size(max = 200) String label,
            Boolean returnStdout) implements Mirrors<ToolCommand> {
    }

    public record UrbanCodeSettingsDto(
            @Size(max = 200) String siteName,
            @Size(max = 200) String deployProcess,
            Boolean skipWait,
            Boolean deployWithSnapshot,
            Boolean updateSnapshotComponents,
            Boolean includeOnlyDeployVersions,
            Boolean deployOnlyChanged,
            @Size(max = 1000) String deployDescription,
            @Size(max = 2000) String requestProperties) implements Mirrors<UrbanCodeSettings> {
    }

    @Builder
    public record UrbanCodeApplicationSettingsDto(
            @NotBlank @Size(max = 200) String applicationName,
            Integer order,
            @Size(max = 20) List<@Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$",
                    message = "environment names may contain letters, digits, '-' and '_'") String> environments,
            @Size(max = 200) String snapshotName,
            @Size(max = 200) String siteName,
            @Size(max = 200) String deployProcess,
            Boolean skipWait,
            Boolean deployWithSnapshot,
            Boolean updateSnapshotComponents,
            Boolean includeOnlyDeployVersions,
            Boolean deployOnlyChanged,
            @Size(max = 1000) String deployDescription,
            @Size(max = 1000) String description,
            @Size(max = 2000) String requestProperties,
            @NotNull @Size(max = 50) List<@NotNull @Valid UrbanCodeComponentDto> components)
            implements Mirrors<UrbanCodeApplicationSettings> {
    }

    public record UrbanCodeComponentDto(
            @NotBlank @Size(max = 200) String componentName,
            @Size(max = 500) String baseDir,
            @Size(max = 500) String fileIncludePatterns,
            @Size(max = 500) String fileExcludePatterns,
            @Size(max = 200) String versionPrefix,
            @Size(max = 200) String version,
            Boolean incrementalVersion,
            @Size(max = 200) String extensions,
            @Size(max = 50) String charset,
            @Size(max = 1000) String pushDescription,
            @Size(max = 2000) String versionProperties,
            @Size(max = 1000) String versionDescription) implements Mirrors<UrbanCodeComponent> {
    }

    @Builder
    public record SshTargetDto(
            @Size(max = 255) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String host,
            @Size(max = 100) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String user,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String deployDir,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String deployScript,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE)
            String versionFile) implements Mirrors<SshTarget> {
    }

    public record OpenShiftTargetDto(
            @Size(max = 200) String projectBuild,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String buildConfigPath,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String dockerFilePath,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String buildContext,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String addFile,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String dockerRepoPush,
            @Size(max = 500) String dockerRepoPull,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String certDir,
            @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String nexusAuthFile,
            @Size(max = 200) String projectDeployment,
            @Size(max = 500) String deployConfigPath,
            @Size(max = 500) String configPath,
            Boolean skipConfigDeploy,
            @Size(max = 500) String healthCheckUrl,
            @Size(max = 300) String routeHostname,
            @Size(max = 500) String deploymentPath,
            @Size(max = 1000)
            @Pattern(regexp = GIT_URL, message = "must be a Git repository URL")
            String deploymentRepoUrl,
            @Size(max = 200) String deploymentRepoBranch,
            @Size(max = 200) String deploymentRepoCredentialsId,
            @Size(max = 500) @Pattern(regexp = IMAGE_TAG, message = IMAGE_TAG_MESSAGE) String buildTag,
            @Size(max = 500) @Pattern(regexp = IMAGE_TAG, message = IMAGE_TAG_MESSAGE) String internalDockerUrl)
            implements Mirrors<OpenShiftTarget> {
    }

    public record AppScanSettingsDto(
            @NotBlank
            @Pattern(regexp = "^\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*$",
                    message = "must be the AppScan application ID, a UUID such as 109f44ac-cc06-4ca0-884e-d944904f7019")
            String applicationId,
            @Size(max = 200) String sastScanName,
            @Size(max = 30)
            List<@Pattern(regexp = FOLDER, message = FOLDER_MESSAGE) String> includedDirs,
            @Size(max = 30)
            List<@Pattern(regexp = FOLDER, message = FOLDER_MESSAGE) String> excludedDirs,
            Boolean compile,
            Boolean sourceCodeOnly,
            Boolean useConfigFile,
            Boolean insecureTls,
            @Size(max = 500) @Pattern(regexp = POWERSHELL_PATH, message = POWERSHELL_PATH_MESSAGE) String clientPath,
            @Valid ToolCommandDto compileCommand,
            Boolean dastEnabled,
            @Size(max = 200) String dastScanName,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String dastTargetUrl,
            @Size(max = 100) String dastPresenceId,
            @Size(max = 200) String secretCredentialsId) implements Mirrors<AppScanSettings> {
    }

    public record SonarSettingsDto(
            @Size(max = 200) String projectName,
            @Size(max = 400) @Pattern(regexp = "^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$",
                    message = "may contain letters, digits, '-', '_', '.' and ':' with at least one non-digit")
            String projectKey,
            @Size(max = 200) String installationName,
            @Size(max = 200) String credentialsId,
            @Size(max = 200) String authTokenCredentialsId,
            @Size(max = 200)
            @Pattern(regexp = "^[A-Za-z0-9_]*$", message = "must be a SonarQube badge token such as sqb_1a2b3c")
            String badgeToken,
            Boolean addBadges,
            Boolean fullBadges,
            @Valid ToolCommandDto command,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String serverUrl)
            implements Mirrors<SonarSettings> {
    }

    public record NexusIqSettingsDto(
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String serverUrl,
            @Size(max = 200) String credentialsId,
            @Size(max = 200) String scaScanName) implements Mirrors<NexusIqSettings> {
    }

    public record NexusIqApplicationDto(
            @NotBlank @Size(max = 200) String application,
            @Size(max = 20) List<@NotBlank @Size(max = 300) String> scanPatterns,
            @Size(max = 50)
            @Pattern(regexp = "^[a-z-]*$", message = "must be a Nexus IQ stage such as build, stage-release or release")
            String stage,
            Boolean failOnNetworkError) implements Mirrors<NexusIqApplication> {
    }

    public record ScmSettingsDto(
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String repositoryUrl,
            @Size(max = 200) String credentialsId,
            BitbucketAuthType authType,
            BitbucketType type,
            @Size(max = 200) String targetBranch,
            @Size(max = 1000)
            @Pattern(regexp = GIT_URL, message = "must be an http, https, ssh or git@ URL")
            String cloneUrl,
            @Size(max = 20)
            List<@Pattern(regexp = "^[^,\\s]{1,100}$", message = "one Bitbucket user name or account UUID per entry")
                    String> reviewers,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String apiUrl,
            @Size(max = 200) @Pattern(regexp = NO_WHITESPACE, message = NO_WHITESPACE_MESSAGE) String workspace,
            @Size(max = 200) @Pattern(regexp = NO_WHITESPACE, message = NO_WHITESPACE_MESSAGE) String projectKey,
            @Size(max = 200) @Pattern(regexp = NO_WHITESPACE, message = NO_WHITESPACE_MESSAGE)
            String repoSlug) implements Mirrors<ScmSettings> {
    }

    public record GoldenFixPolicyDto(
            Boolean enabled,
            Boolean onlyDirectDependencies,
            @Min(0) @Max(10) Integer minThreatLevel,
            @Size(max = 4)
            List<@Pattern(regexp = "^(maven|npm|pypi|pub)$", message = "must be maven, npm, pypi or pub") String> ecosystems,
            @Size(max = 10)
            List<@Pattern(regexp = "^[a-z-]{1,100}$", message = "must be a Nexus IQ remediation type such as recommended-non-breaking")
                    String> goldenVersionTypes,
            @Size(max = 30) List<@Size(min = 1, max = 200) String> excludeDirs,
            Boolean verifyEnabled,
            @Min(1) Integer verifyMaxAttempts,
            @Min(1) Integer verifyTimeoutMinutes,
            @Size(max = 500) String verifyMavenCommand,
            @Size(max = 500) String verifyGradleCommand,
            @Size(max = 500) String verifyNpmCommand,
            @Size(max = 500) String verifyPipCommand,
            @Size(max = 500) String verifyPubCommand,
            @Size(max = 200) String commitAuthorName,
            @Email @Size(max = 320) String commitAuthorEmail,
            @Size(max = 100)
            @Pattern(regexp = "^[A-Za-z0-9_+/-]*$", message = "must be a time zone ID such as Europe/Warsaw or UTC")
            String timeZone) implements Mirrors<GoldenFixPolicy> {
    }

    public record MetricsSettingsDto(
            Boolean enabled,
            @Size(max = 200) String influxProject,
            @Size(max = 50)
            @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, '.', '-' and '_'")
            String influxEnv,
            @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String influxUrl,
            @Size(max = 200) String influxCredentialsId) implements Mirrors<MetricsSettings> {
    }

    public record FlutterSettingsDto(
            FlutterPlatform platform,
            @Size(max = 30) List<@Pattern(regexp = MODULE, message = MODULE_MESSAGE) String> modules,
            @Size(max = 30) List<@Pattern(regexp = MODULE, message = MODULE_MESSAGE) String> testModules,
            @Size(max = 30) List<@Pattern(regexp = MODULE, message = MODULE_MESSAGE) String> testSubmodules,
            @Size(max = 30) List<@Pattern(regexp = MODULE, message = "must be a plugin folder name") String> testSubplugins,
            @Size(max = 200) String signingPasswordCredentialsId,
            @Size(max = 200) String prodLicenseCredentialsId,
            @Size(max = 200) String testLicenseCredentialsId,
            @Size(max = 200) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String deliveryGroup,
            @Size(max = 200) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String deliveryArtifact,
            @Size(max = 300) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String deliveryPlugin,
            @Size(max = 500) String sonarSources,
            @Size(max = 500) String sonarTests,
            Boolean sonarFlutterPlugin,
            @Size(max = 500) String dartAnalyzeCommand,
            @Size(max = 50)
            @Pattern(regexp = "^[0-9A-Za-z._-]*$", message = "must be a SonarScanner version such as 5.0.1.3006")
            String sonarScannerVersion) implements Mirrors<FlutterSettings> {
    }
}
