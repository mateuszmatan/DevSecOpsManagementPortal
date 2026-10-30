package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.in.web.ConfigImportResponse.ImportedPipeline;
import com.bbh.itss.dso.portal.adapter.in.web.ConfigImportResponse.ImportedService;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketType;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.TestJobType;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.adapter.in.web.ConfigFileParser.invalid;
import static com.bbh.itss.dso.portal.adapter.in.web.ConfigInput.UNKNOWN;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings.ONE_APPLICATION;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static com.bbh.itss.dso.portal.adapter.in.web.ConfigInput.valueAt;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Reads a config.yaml, a rendered product or pipeline config, or their JSON form into new service forms: the inverse of
 * the services' writeTo, as a table from config keys to request fields. A value the portal writes into every project
 * from the global settings, or one equal to the Library default the project inherits (tests.maxParallel, GoldenFix,
 * the Node.js installation and npm settings) or to the library's own default of a Node.js build setting, comes back
 * unset, so the service keeps inheriting it. A value the portal cannot hold becomes a note, not an error, so a partial
 * import still fills the form.
 */
final class ServiceConfigReader {

    static final String GLOBAL = "not imported: a global setting, the same for every service";
    static final String PIPELINE = "not imported: a pipeline setting, chosen when the pipeline is created";
    static final String RUN_TIME = "check: a run that builds the image replaces it";
    static final String NODE_WITH_FLUTTER = "check: the Node.js build is not used with Flutter";
    static final String NO_PROJECT = "found no project: expected a projects map or the settings of one project";
    static final int MAX_PROJECTS = 50;

    private static final List<String> DOCUMENT_KEYS = List.of("pipeline", "platform", "defaults");

    private static final Conversion TEXT = ServiceConfigReader::text;
    private static final Conversion FLAG = value -> switch (text(value).trim().toLowerCase(ROOT)) {
        case "true" -> true;
        case "false" -> false;
        default -> throw expected("true or false");
    };
    private static final Conversion NUMBER = value -> {
        try {
            return new BigDecimal(text(value).trim()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw expected("a whole number");
        }
    };
    private static final Conversion LIST = value -> items(value, null);
    private static final Conversion COMMAS = value -> items(value, ",");
    private static final Conversion WORDS = value -> items(value, "\\s+");
    private static final Conversion ENV = value -> {
        if (value instanceof Map<?, ?> variables) {
            return pairs(variables);
        }
        throw expected("a map of variables");
    };
    private static final Conversion PARAMETERS = value ->
            String.join("\n", value instanceof Map<?, ?> parameters ? pairs(parameters) : items(value, null));
    private static final Conversion DISABLED = value -> !(Boolean) FLAG.apply(value);
    private static final Conversion IGNORED = value -> null;
    private static final Conversion BITBUCKET_AUTH = choice(BitbucketAuthType.values(), Enum::name);
    private static final Conversion AUTH_TYPE = value ->
            BITBUCKET_AUTH.apply("token".equalsIgnoreCase(text(value).trim()) ? "bearer" : value);
    private static final Conversion CREDENTIALS_AUTH = value -> {
        if (value instanceof Map<?, ?> auth && "CredentialsAuth".equals(auth.get("$class"))
                && auth.get("credentials") != null) {
            return text(auth.get("credentials"));
        }
        throw expected("CredentialsAuth with its credentials ID");
    };

    private static final List<Rule> DEFAULTED = List.of(
            rule("buildTool", "build.tool", choice(BuildTool.values(), Enum::name)),
            rule("deployTarget", "deployment.target", choice(DeployTarget.values(), Enum::name)),
            rule("sourceDir", "build.sourceDir", TEXT));
    private static final List<Rule> PROJECT = projectRules();
    private static final Set<String> PROJECT_KEYS = Stream.concat(DEFAULTED.stream(), PROJECT.stream())
            .map(rule -> rule.key().split("\\.")[0]).collect(toSet());
    private static final String DELIVERY = "deploy.%s.rd.nexusDeliveryEnabled";
    private static final List<Block> COMMAND_BLOCKS = List.of(new Block("build", "build.command"),
            new Block("tests.unitTests", "unitTests.command"), new Block("tools.sonar", "sonar.command"),
            new Block("asoc", "appScan.compileCommand"), new Block("delivery", "delivery"));
    private static final List<Rule> COMMAND = List.of(rule("flags", "flags", WORDS), rule("dir", "directory", TEXT),
            rule("env", "environment", ENV), rule("label", "label", TEXT), rule("returnStdout", "returnStdout", FLAG));
    private static final Map<BuildTool, List<Rule>> COMMANDS = Map.of(
            GRADLE, concat(List.of(rule("tasks", "tasks", WORDS)), COMMAND),
            MAVEN, concat(List.of(rule("goals", "tasks", WORDS), rule("mvnPath", "mavenHome", TEXT)), COMMAND));
    private static final List<Rule> TEST_JOB = concat(
            same(TEXT, "name", "remoteJenkins", "remoteJenkinsUrl", "credentialsId", "tokenCredentialsId"),
            same(FLAG, "abortTriggeredJob", "overrideTrustAllCertificates", "preventRemoteBuildQueue",
                    "trustAllCertificates", "useCrumbCache", "useJobInfoCache"),
            List.of(rule("type", "type", choice(TestJobType.values(), Enum::name)), rule("job", "job", TEXT),
                    rule("url", "job", TEXT), rule("timeoutMin", "timeoutMinutes", NUMBER),
                    rule("parameters", "parameters", PARAMETERS), rule("pollIntervalSec", "pollIntervalSec", NUMBER),
                    rule("auth", "credentialsId", CREDENTIALS_AUTH)));
    private static final List<Rule> URBAN_CODE_APPLICATION = concat(
            same(TEXT, "applicationName", "snapshotName", "siteName", "deployProcess", "deployDescription",
                    "description", "requestProperties"),
            same(FLAG, "skipWait", "deployWithSnapshot", "includeOnlyDeployVersions", "deployOnlyChanged"),
            List.of(rule("order", "order", NUMBER), rule("environments", "environments", LIST),
                    rule("updateSnapshotComp", "updateSnapshotComponents", FLAG)));
    private static final List<Rule> URBAN_CODE_COMPONENT = concat(
            same(TEXT, "componentName", "baseDir", "fileIncludePatterns", "fileExcludePatterns", "versionPrefix",
                    "version", "extensions", "charset", "pushDescription", "versionProperties", "versionDescription"),
            same(FLAG, "incrementalVersion"));
    private static final List<Rule> NEXUS_IQ_APPLICATION = List.of(rule("application", "application", TEXT),
            rule("scanPatterns", "scanPatterns", LIST), rule("stage", "stage", TEXT),
            rule("failOnNetworkError", "failOnNetworkError", FLAG));
    private static final List<Rule> PIPELINE_RULES = List.of(
            rule("type", "type", choice(PipelineType.values(), PipelineType::variant)),
            rule("agentNames", "agentLabels", COMMAS), rule("entryPoint", null, IGNORED),
            rule("product", null, IGNORED), rule("projectNames", null, IGNORED), note("securityPipeline", PIPELINE));

    private final GlobalSettingsValues global;
    private final JsonMapper json;
    private final Map<String, Object> inherited;

    ServiceConfigReader(GlobalSettingsValues global, JsonMapper json) {
        this.global = global;
        this.json = json;
        ConfigTree inherited = new ConfigTree().set("deploy.vm.dod.applications", List.of(Map.of()));
        global.platform().writeProjectDefaults(inherited);
        global.deployment().fillIn(inherited, VM);
        global.goldenFix().writeTo(inherited);
        inherited.set("tests.maxParallel", global.serviceDefaults().testsMaxParallel());
        global.serviceDefaults().writeNodeTo(inherited);
        inherited.set("build.node.commands", List.of("npm ci", "npm run build")).set("build.node.timeoutMin", 30)
                .set("build.node.nexusIqScan", true);
        this.inherited = inherited.toMap();
    }

    ConfigImportResponse read(Object document, String serviceName) {
        Map<?, ?> root = document instanceof Map<?, ?> map ? map : Map.of();
        List<FieldProblem> notes = new ArrayList<>();
        ConfigInput file = new ConfigInput(root, Map.of(), notes);
        ConfigInput pipelineInput = file.child("pipeline", Map.of());
        Object name = isBlank(serviceName) ? pipelineInput.take("projectNames") : serviceName;
        ImportedPipeline pipeline = root.get("pipeline") instanceof Map<?, ?> ? pipeline(pipelineInput) : null;
        ConfigInput fileDefaults = file.child("defaults", global.defaultsConfig());
        Map<?, ?> projects = root.containsKey("projects")
                ? file.take("projects") instanceof Map<?, ?> named ? named : Map.of()
                : Collections.singletonMap(name, projectOf(root));
        if (projects.size() > MAX_PROJECTS) {
            throw InvalidRequestException.of("content", "holds more than %d projects".formatted(MAX_PROJECTS));
        }

        List<ImportedService> services = new ArrayList<>();
        projects.forEach((project, settings) -> {
            if (settings instanceof Map<?, ?> map && !map.isEmpty()) {
                services.add(service(project == null ? null : String.valueOf(project), map, fileDefaults));
            } else {
                file.note("projects." + project, "not imported: expected the settings of the project");
            }
        });
        if (services.isEmpty()) {
            throw invalid(1, 1, NO_PROJECT);
        }
        file.child("platform", global.platform().toConfig()).noteUnread(GLOBAL);
        fileDefaults.noteUnread(GLOBAL);
        if (root.containsKey("projects")) {
            file.noteUnread(UNKNOWN);
        }
        return new ConfigImportResponse(services, pipeline, notes.stream().distinct().toList());
    }

    private ImportedPipeline pipeline(ConfigInput input) {
        ConfigTree pipeline = read(input, PIPELINE_RULES, new ConfigTree());
        input.noteUnread(UNKNOWN);
        return json.convertValue(pipeline.toMap(), ImportedPipeline.class);
    }

    private ImportedService service(String name, Map<?, ?> settings, ConfigInput fileDefaults) {
        List<FieldProblem> notes = new ArrayList<>();
        ConfigInput project = new ConfigInput(settings, inherited, notes);
        ConfigTree request = new ConfigTree();
        ConfigInput globalDefaults = new ConfigInput(global.defaultsConfig(), Map.of(), new ArrayList<>());
        for (Rule rule : DEFAULTED) {
            for (ConfigInput source : List.of(project, fileDefaults, globalDefaults)) {
                if (request.get(rule.field()) == null) {
                    read(source, List.of(rule), request);
                }
            }
        }
        BuildTool tool = BuildTool.valueOf(String.valueOf(request.get("build.tool")));
        DeployTarget target = DeployTarget.valueOf(String.valueOf(request.get("deployment.target")));
        read(project, PROJECT, request);
        if (tool == FLUTTER && Boolean.TRUE.equals(request.get("nodeBuild.enabled"))) {
            request.set("nodeBuild.enabled", false);
            project.note("build.node.enabled", NODE_WITH_FLUTTER);
        }
        readCommands(project, tool, request);
        readDelivery(project, target, request);
        request.set("testJobs", testJobs(project))
                .set("urbanCodeApplications", project.entries("deploy.vm.dod.applications", null, null).stream()
                        .map(application -> entry(application, URBAN_CODE_APPLICATION, new ConfigTree()
                                .set("components", application.entries("components", null, null).stream()
                                        .map(component -> entry(component, URBAN_CODE_COMPONENT, new ConfigTree()))
                                        .toList())))
                        .toList())
                .set("nexusIqApplications", nexusIqApplications(project, settings));
        boolean delivers = !Boolean.TRUE.equals(request.get("deployment.nexusDeliveryDisabled"));
        for (String key : List.of("buildTag", "internalDockerUrl")) {
            if (request.get("openShiftTargets.RD." + key) != null && delivers) {
                project.note("deploy.openshift.rd." + key, RUN_TIME);
            }
        }
        project.noteUnread(UNKNOWN);

        Map<String, Object> fields = request.toMap();
        fields.putIfAbsent("appScan", Map.of());
        Service service = new Service(null, name, null, 0,
                json.convertValue(fields, ServiceDto.class).toDraft().settings());
        return new ImportedService(name, ServiceDto.from(service), notes);
    }

    /** Reads the command block of the service's build tool; a block of another tool is left out with a note. */
    private static void readCommands(ConfigInput project, BuildTool tool, ConfigTree request) {
        for (Block block : COMMAND_BLOCKS) {
            for (BuildTool blockTool : List.of(GRADLE, MAVEN)) {
                String key = block.key() + "." + blockTool.name().toLowerCase(ROOT);
                if (blockTool == tool) {
                    read(project, prefixed(key + ".", block.field() + ".", COMMANDS.get(tool)), request);
                } else if (project.take(key) != null) {
                    project.note(key, "not imported: the service builds with " + tool.name().toLowerCase(ROOT));
                }
            }
        }
    }

    /** Reads the delivery switch of the service's deploy target; the switch of the other target is left out. */
    private static void readDelivery(ConfigInput project, DeployTarget target, ConfigTree request) {
        for (DeployTarget each : DeployTarget.values()) {
            String key = DELIVERY.formatted(each.name().toLowerCase(ROOT));
            if (each == target) {
                read(project, List.of(rule(key, "deployment.nexusDeliveryDisabled", DISABLED)), request);
            } else if (project.take(key) != null) {
                project.note(key, "not imported: the service deploys to " + target.name().toLowerCase(ROOT));
            }
        }
    }

    /** The jobs of every test stage, with the stage's defaults under each and its URLs as jobs of their own. */
    private static List<Map<String, Object>> testJobs(ConfigInput project) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        for (TestStage stage : TestStage.values()) {
            String path = "tests." + stage.configKey() + ".";
            ConfigInput defaults = project.child(path + "defaults", Map.of());
            for (String list : List.of("jobs", "urls")) {
                project.entries(path + list, "url", defaults).forEach(job ->
                        jobs.add(entry(job, TEST_JOB, new ConfigTree().set("stage", stage.name()))));
            }
            defaults.noteUnread(UNKNOWN);
        }
        return jobs;
    }

    /**
     * The Nexus IQ application: a name with its settings beside it, or, in the form of legacy services, a map of
     * applications whose first entry is taken. Each entry repeats the server, which is inherited from the section.
     */
    private static List<Map<String, Object>> nexusIqApplications(ConfigInput project, Map<?, ?> settings) {
        String path = "tools.nexusIq.";
        if (!(project.take(path + "application") instanceof Map<?, ?> applications)) {
            ConfigTree application = read(project, prefixed(path, "", NEXUS_IQ_APPLICATION), new ConfigTree());
            return application.get("application") == null ? List.of() : List.of(application.toMap());
        }
        Map<String, Object> server = new ConfigTree().set("serverUrl", valueAt(settings, path + "serverUrl"))
                .set("credentialsId", valueAt(settings, path + "credentialsId")).toMap();
        List<Map<String, Object>> taken = new ArrayList<>();
        applications.forEach((name, entry) -> {
            String key = path + "application." + name;
            if (taken.isEmpty()) {
                ConfigInput input = project.nested(key, entry instanceof Map<?, ?> map ? map : Map.of(), server, null);
                taken.add(entry(input, NEXUS_IQ_APPLICATION, new ConfigTree().set("application", name)));
            } else {
                project.note(key, "not imported: " + ONE_APPLICATION);
            }
        });
        return taken;
    }

    private static Map<String, Object> entry(ConfigInput input, List<Rule> rules, ConfigTree entry) {
        read(input, rules, entry);
        input.noteUnread(UNKNOWN);
        return entry.toMap();
    }

    private static ConfigTree read(ConfigInput input, List<Rule> rules, ConfigTree request) {
        for (Rule rule : rules) {
            Object value = input.take(rule.key());
            if (value != null) {
                try {
                    request.set(rule.field(), rule.conversion().apply(value));
                } catch (NotImported e) {
                    input.note(rule.key(), e.getMessage());
                }
            }
        }
        return request;
    }

    /** The settings of the one project a file without a projects map describes: none when it has no project key. */
    private static Map<?, ?> projectOf(Map<?, ?> root) {
        Map<Object, Object> project = new LinkedHashMap<>(root);
        project.keySet().removeAll(DOCUMENT_KEYS);
        return project.keySet().stream().anyMatch(PROJECT_KEYS::contains) ? project : Map.of();
    }

    private static List<Rule> projectRules() {
        List<Rule> rules = new ArrayList<>(List.of(
                rule("javaPath", "build.javaPath", TEXT),
                rule("buildToolAutoSetup", "build.autoSetup", FLAG),
                rule("build.buildPath", "build.buildPath", TEXT),
                rule("appId", "appScan.applicationId", TEXT),
                rule("includedDirs", "appScan.includedDirs", COMMAS),
                rule("excludedDirs", "appScan.excludedDirs", COMMAS),
                rule("appscanPath", "appScan.clientPath", TEXT),
                note("asoc.url", GLOBAL),
                rule("asoc.keyId", "appScan.keyId", TEXT),
                rule("asoc.token", "appScan.secretCredentialsId", TEXT),
                rule("asoc.doCompile", "appScan.compile", FLAG),
                rule("asoc.useAppScanConfig", "appScan.useConfigFile", FLAG),
                rule("sast.scanName", "appScan.scanName", TEXT),
                rule("dast.enabled", "appScan.dastEnabled", FLAG),
                rule("dast.targetUrl", "appScan.dastTargetUrl", TEXT),
                rule("dast.presenceId", "appScan.dastPresenceId", TEXT),
                note("jenkins.pipeline.extendedPipeline", PIPELINE),
                rule("influx.enabled", "metrics.enabled", FLAG),
                rule("influx.url", "metrics.influxUrl", TEXT),
                rule("influx.credentialsId", "metrics.influxCredentialsId", TEXT),
                rule("influx.project", "metrics.influxProject", TEXT),
                rule("influx.env", "metrics.influxEnv", TEXT),
                rule("tests.unitTests.unitTestResult", "unitTests.resultPattern", TEXT),
                rule("coverage.reportPath", "unitTests.coverageReportPath", TEXT),
                rule("tests.maxParallel", "tests.maxParallel", NUMBER),
                rule("tools.sonar.authToken", "sonar.authTokenCredentialsId", TEXT),
                rule("scm.bitbucket.url", "scm.repositoryUrl", TEXT),
                rule("scm.bitbucket.authType", "scm.authType", AUTH_TYPE),
                rule("scm.bitbucket.type", "scm.type", choice(BitbucketType.values(), Enum::name)),
                rule("scm.bitbucket.reviewers", "scm.reviewers", COMMAS),
                rule("goldenFix.minThreatLevel", "goldenFix.minThreatLevel", NUMBER),
                rule("goldenFix.verify.enabled", "goldenFix.verifyEnabled", FLAG),
                rule("goldenFix.verify.maxAttempts", "goldenFix.verifyMaxAttempts", NUMBER),
                rule("goldenFix.verify.timeoutMinutes", "goldenFix.verifyTimeoutMinutes", NUMBER),
                rule("deploy.vm.dod.updateSnapshotComp", "urbanCode.updateSnapshotComponents", FLAG),
                rule("flutter.platform", "flutter.platform", choice(FlutterPlatform.values(), Enum::name)),
                rule("tools.flutter.flutterModules", "flutter.modules", LIST),
                rule("tests.modules", "flutter.testModules", LIST),
                rule("tests.submodules", "flutter.testSubmodules", LIST),
                rule("tests.subplugins", "flutter.testSubplugins", LIST),
                rule("build.credentialsId.0", "flutter.signingPasswordCredentialsId", TEXT),
                rule("build.credentialsId.1", "flutter.prodLicenseCredentialsId", TEXT),
                rule("build.credentialsId.2", "flutter.testLicenseCredentialsId", TEXT),
                rule("delivery.group", "flutter.deliveryGroup", TEXT),
                rule("delivery.artifact", "flutter.deliveryArtifact", TEXT),
                rule("delivery.plugin", "flutter.deliveryPlugin", TEXT),
                rule("tools.sonar.sources", "flutter.sonarSources", TEXT),
                rule("tools.sonar.tests", "flutter.sonarTests", TEXT),
                rule("tools.sonar.dartAnalyzeCommand", "flutter.dartAnalyzeCommand", TEXT),
                rule("tools.sonar.sonarScannerVersion", "flutter.sonarScannerVersion", TEXT),
                rule("tools.sonar.flutterPlugin", "flutter.sonarFlutterPlugin", FLAG)));
        rules.addAll(prefixed("", "deployment.", same(TEXT, "appName", "artifactName", "baseArtifactName")));
        rules.addAll(prefixed("build.node.", "nodeBuild.", concat(same(FLAG, "enabled", "nexusIqScan"),
                same(TEXT, "installationName", "nodePath", "registry", "npmrcCredentialsId"),
                List.of(rule("dir", "directory", TEXT), rule("commands", "commands", LIST),
                        rule("env", "environment", ENV), rule("timeoutMin", "timeoutMinutes", NUMBER)))));
        rules.addAll(prefixed("asoc.", "appScan.", same(FLAG, "sourceCodeOnly", "insecureTls")));
        rules.addAll(prefixed("tests.unitTests.", "unitTests.", concat(same(TEXT, "rootDir", "reportOutDir"),
                same(FLAG, "allowEmptyResults"))));
        rules.addAll(prefixed("tools.sonar.", "sonar.", concat(same(TEXT, "serverUrl", "projectName", "projectKey",
                "installationName", "credentialsId", "badgeToken", "badgeCredentialsId"),
                same(FLAG, "addBadges", "fullBadges"))));
        rules.addAll(prefixed("tools.nexusIq.", "nexusIq.", same(TEXT, "serverUrl", "credentialsId")));
        rules.addAll(prefixed("scm.bitbucket.", "scm.", same(TEXT, "credentialsId", "targetBranch", "cloneUrl",
                "apiUrl", "workspace", "projectKey", "repoSlug")));
        rules.addAll(prefixed("goldenFix.", "goldenFix.", concat(same(FLAG, "enabled", "onlyDirectDependencies"),
                same(LIST, "ecosystems", "goldenVersionTypes", "excludeDirs"),
                same(TEXT, "commitAuthorName", "commitAuthorEmail", "timeZone"))));
        rules.add(rule("goldenFix.versionCatalogs", "versionCatalogs", LIST));
        for (String tool : List.of("maven", "gradle", "npm", "pip", "pub")) {
            rules.add(rule("goldenFix.verify.commands." + tool, "goldenFix.verify" + capitalize(tool) + "Command",
                    TEXT));
        }
        rules.addAll(prefixed("deploy.vm.dod.", "urbanCode.", concat(
                same(TEXT, "siteName", "deployProcess", "deployDescription", "requestProperties"),
                same(FLAG, "skipWait", "deployWithSnapshot", "includeOnlyDeployVersions", "deployOnlyChanged"))));
        for (TestStage stage : TestStage.values()) {
            rules.addAll(prefixed("tests." + stage.configKey() + ".", "tests." + stage.configKey(), List.of(
                    rule("required", "Required", FLAG), rule("maxParallel", "MaxParallel", NUMBER),
                    rule("pollIntervalSec", "PollIntervalSec", NUMBER))));
        }
        List<Rule> openShiftTarget = concat(
                same(TEXT, "buildConfigPath", "dockerFilePath", "buildContext", "addFile", "deployConfigPath",
                        "healthCheckUrl", "deploymentPath", "buildTag", "internalDockerUrl"),
                same(FLAG, "skipConfigDeploy"),
                List.of(rule("projectBuildR", "projectBuild", TEXT), rule("qcDockerRepoPush", "dockerRepoPush", TEXT),
                        rule("qcDockerRepoPull", "dockerRepoPull", TEXT), rule("openshiftCertDir", "certDir", TEXT),
                        rule("nexus.authfile", "nexusAuthFile", TEXT),
                        rule("projectDeploymentR", "projectDeployment", TEXT), rule("configPathR", "configPath", TEXT),
                        rule("routeHostnameR", "routeHostname", TEXT),
                        rule("deploymentRepo.url", "deploymentRepoUrl", TEXT),
                        rule("deploymentRepo.branch", "deploymentRepoBranch", TEXT),
                        rule("deploymentRepo.credentials", "deploymentRepoCredentialsId", TEXT)));
        for (Region region : Region.values()) {
            rules.addAll(prefixed("deploy.vm." + region.configKey() + ".", "sshTargets." + region + ".",
                    same(TEXT, "host", "user", "deployDir", "deployScript", "versionFile")));
            rules.addAll(prefixed("deploy.openshift." + region.configKey() + ".", "openShiftTargets." + region + ".",
                    openShiftTarget));
        }
        return List.copyOf(rules);
    }

    private static String text(Object value) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            throw expected("a single value");
        }
        return String.valueOf(value);
    }

    private static List<String> items(Object value, String separator) {
        if (value instanceof Collection<?> items) {
            return items.stream().filter(Objects::nonNull).map(ServiceConfigReader::text).toList();
        }
        return separator == null ? List.of(text(value)) : List.of(text(value).trim().split(separator));
    }

    private static List<String> pairs(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(pair -> pair.getKey() + "=" + text(Objects.requireNonNullElse(pair.getValue(), "")))
                .toList();
    }

    /** A value spelled as the config writes it, in any case, read as the request's spelling. */
    private static <E extends Enum<E>> Conversion choice(E[] constants, Function<E, String> spelling) {
        Map<String, String> choices = Arrays.stream(constants).collect(toMap(constant ->
                spelling.apply(constant).toLowerCase(ROOT), Enum::name, (first, second) -> first, LinkedHashMap::new));
        return value -> {
            String choice = choices.get(text(value).trim().toLowerCase(ROOT));
            if (choice == null) {
                throw expected("one of " + String.join(", ", choices.keySet()));
            }
            return choice;
        };
    }

    private static NotImported expected(String what) {
        return new NotImported("not imported: expected " + what);
    }

    private static Rule rule(String key, String field, Conversion conversion) {
        return new Rule(key, field, conversion);
    }

    private static Rule note(String key, String message) {
        return new Rule(key, null, value -> {
            throw new NotImported(message);
        });
    }

    private static List<Rule> same(Conversion conversion, String... keys) {
        return Arrays.stream(keys).map(key -> rule(key, key, conversion)).toList();
    }

    private static List<Rule> prefixed(String keyPrefix, String fieldPrefix, List<Rule> rules) {
        return rules.stream()
                .map(rule -> rule(keyPrefix + rule.key(), fieldPrefix + rule.field(), rule.conversion()))
                .toList();
    }

    @SafeVarargs
    private static List<Rule> concat(List<Rule>... lists) {
        return Stream.of(lists).flatMap(List::stream).toList();
    }

    /** A config key, the request field it fills and how its value converts. */
    private record Rule(String key, String field, Conversion conversion) {
    }

    /** A command block, such as build.gradle, and the request field of its command. */
    private record Block(String key, String field) {
    }

    @FunctionalInterface
    private interface Conversion {

        Object apply(Object value);
    }

    /** A value the import leaves out; its message is the note. */
    private static final class NotImported extends RuntimeException {

        NotImported(String message) {
            super(message, null, false, false);
        }
    }
}
