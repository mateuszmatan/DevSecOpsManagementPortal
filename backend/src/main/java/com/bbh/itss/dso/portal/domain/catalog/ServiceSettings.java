package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.With;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.LIQUIBASE;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy.INHERITED;
import static com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget.NONE;
import static com.bbh.itss.dso.portal.domain.catalog.Region.QC;
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD;
import static com.bbh.itss.dso.portal.domain.catalog.TestSettings.DEFAULTS;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.dso.portal.domain.shared.Text.relativePaths;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

@Builder
public record ServiceSettings(BuildSettings build, NodeBuildSettings nodeBuild, UnitTestSettings unitTests,
                              TestSettings tests, List<TestJob> testJobs, DeploymentSettings deployment,
                              ToolCommand delivery, UrbanCodeSettings urbanCode,
                              List<UrbanCodeApplicationSettings> urbanCodeApplications,
                              Map<Region, SshTarget> sshTargets, Map<Region, OpenShiftTarget> openShiftTargets,
                              AppScanSettings appScan, SonarSettings sonar, NexusIqSettings nexusIq,
                              List<NexusIqApplication> nexusIqApplications, ScmSettings scm, GoldenFixPolicy goldenFix,
                              List<String> versionCatalogs, VersionCatalogRepo versionCatalogRepo,
                              @With(PRIVATE) MetricsSettings metrics, FlutterSettings flutter,
                              @With(PRIVATE) DatabaseSettings database,
                              Map<DatabaseTarget, DatabaseTargetSettings> databaseTargets) {

    public ServiceSettings {
        requireNonNull(build, "a service needs its build settings");
        requireNonNull(deployment, "a service needs its deployment settings");
        requireNonNull(appScan, "a service needs its AppScan settings");
        nodeBuild = getIfNull(nodeBuild, NodeBuildSettings.NONE);
        unitTests = getIfNull(unitTests, UnitTestSettings.NONE);
        tests = getIfNull(tests, DEFAULTS);
        testJobs = List.copyOf(emptyIfNull(testJobs));
        delivery = getIfNull(delivery, ToolCommand.NONE);
        urbanCode = getIfNull(urbanCode, UrbanCodeSettings.DEFAULTS);
        urbanCodeApplications = List.copyOf(emptyIfNull(urbanCodeApplications));
        sshTargets = withoutEmpty(Region.class, sshTargets, SshTarget::isEmpty);
        openShiftTargets = withoutEmpty(Region.class, openShiftTargets, OpenShiftTarget::isEmpty);
        sonar = getIfNull(sonar, SonarSettings.NONE);
        nexusIq = getIfNull(nexusIq, NexusIqSettings.NONE);
        nexusIqApplications = List.copyOf(emptyIfNull(nexusIqApplications));
        scm = getIfNull(scm, ScmSettings.NONE);
        goldenFix = getIfNull(goldenFix, INHERITED);
        versionCatalogs = relativePaths(versionCatalogs);
        versionCatalogRepo = getIfNull(versionCatalogRepo, VersionCatalogRepo.NONE);
        metrics = getIfNull(metrics, MetricsSettings.DEFAULTS);
        flutter = getIfNull(flutter, FlutterSettings.NONE);
        database = getIfNull(database, DatabaseSettings.NONE);
        Map<DatabaseTarget, DatabaseTargetSettings> databases = new EnumMap<>(DatabaseTarget.class);
        emptyIfNull(databaseTargets).forEach((target, settings) -> {
            if (target != null && settings != null) {
                databases.put(target, settings.withoutDefaults(target));
            }
        });
        databaseTargets = withoutEmpty(DatabaseTarget.class, databases, DatabaseTargetSettings::isEmpty);
    }

    public ServiceSettings withDefaultMetricsProject(String productCode, String serviceName) {
        return withMetrics(metrics.withDefaultProject(productCode, serviceName));
    }

    public ServiceSettings withDefaultArtifact(String serviceName) {
        return withDatabase(database.withDefaultArtifact(serviceName));
    }

    /** Whether Liquibase deploys the changes of the service to its databases: a Liquibase service with a changelog. */
    public boolean changesDatabase() {
        return build.tool() == LIQUIBASE && database.changelogFile() != null;
    }

    /** The target the service deploys to; none for a service whose changes Liquibase deploys. */
    public DeployTarget deployTarget() {
        return changesDatabase() ? null : deployment.target();
    }

    public void writeTo(ConfigTree config) {
        BuildTool tool = build.tool();
        boolean deploys = !changesDatabase();
        build.writeTo(config);
        nodeBuild.writeTo(config);
        if (deploys) {
            deployment.writeTo(config);
        }
        appScan.writeTo(config, tool);
        metrics.writeTo(config);
        unitTests.writeTo(config, tool);
        sonar.writeTo(config, tool);
        nexusIq.writeTo(config, nexusIqApplications);
        scm.writeTo(config);
        goldenFix.writeTo(config);
        config.set("goldenFix.versionCatalogs", versionCatalogs);
        versionCatalogRepo.writeTo(config);
        tests.writeTo(config, testJobs);
        if (!deploys) {
            database.writeTo(config, databaseTargets);
        } else if (deployment.target() == VM) {
            if (tool == MAVEN) {
                delivery.writeTo(config, "delivery", MAVEN);
            }
            urbanCode.writeTo(config, urbanCodeApplications);
            sshTargets.forEach((region, target) -> config.set("deploy.vm." + region.configKey(), target.toConfig()));
        } else {
            openShiftTargets.forEach((region, target) ->
                    config.set("deploy.openshift." + region.configKey(), target.toConfig()));
        }
        if (deploys && deployment.nexusDeliveryDisabled()) {
            String rd = deployment.target() == VM ? "deploy.vm.rd" : "deploy.openshift.rd";
            config.set(rd + ".nexusDeliveryEnabled", false);
        }
        if (tool == FLUTTER) {
            flutter.writeTo(config);
        }
    }

    /** The rules of the service; one whose changes Liquibase deploys has no deployment of its own to check. */
    public void validate(ValidationProblems problems) {
        BuildTool tool = build.tool();
        boolean deploys = !changesDatabase();
        build.validate(problems.at("build"));
        nodeBuild.validate(problems.at("nodeBuild"), tool);
        unitTests.validate(problems.at("unitTests"), tool);
        if (deploys) {
            deployment.validate(problems.at("deployment"));
        }
        if (deploys && deployment.target() == OPENSHIFT) {
            if (deployment.deliversToNexus(OPENSHIFT)) {
                openShiftTargets.getOrDefault(RD, NONE).validateImageBuild(problems.at(targetPath(RD)));
            }
            openShiftTargets.getOrDefault(QC, NONE).validateQcDeploy(problems.at(targetPath(QC)));
        }
        appScan.validate(problems.at("appScan"), tool);
        sonar.validate(problems.at("sonar"), tool);
        nexusIq.validate(problems, nexusIqApplications);
        scm.validate(problems.at("scm"));
        goldenFix.validate(problems.at("goldenFix"));
        LINES_2000.check(problems, "versionCatalogs", versionCatalogs);
        versionCatalogRepo.validate(problems.at("versionCatalogRepo"));
        if (tool == FLUTTER) {
            flutter.validate(problems.at("flutter"), deployment);
        }
        if (deploys && deployment.deliversToNexus(VM) && tool == MAVEN) {
            problems.require("build.buildPath", build.buildPath(),
                    "is required for Maven on VMs: the Nexus delivery publishes the artifact found there");
            problems.require("delivery.tasks", delivery.tasks(),
                    "add the Maven goals that upload the snapshot, for example deploy:deploy-file");
        }
        if (deploys) {
            delivery.validate(problems.at("delivery"));
        }
        for (int i = 0; i < testJobs.size(); i++) {
            testJobs.get(i).validate(problems.at("testJobs[" + i + "]"));
        }
        for (int i = 0; deploys && i < urbanCodeApplications.size(); i++) {
            urbanCodeApplications.get(i).validate(problems.at("urbanCodeApplications[" + i + "]"));
        }
        if (database.isSet(databaseTargets) && tool != LIQUIBASE) {
            problems.add("database", "is set for a service that does not build with Liquibase: only a Liquibase"
                    + " service changes a database");
        } else if (database.isSet(databaseTargets)) {
            database.validate(problems, databaseTargets, build);
        }
    }

    private static String targetPath(Region region) {
        return "openShiftTargets[" + region.name() + "]";
    }

    private static <K extends Enum<K>, T> Map<K, T> withoutEmpty(Class<K> keys, Map<K, T> targets,
                                                                  Predicate<T> empty) {
        if (targets == null) {
            return Map.of();
        }
        Map<K, T> kept = new EnumMap<>(keys);
        targets.forEach((region, target) -> {
            if (region != null && target != null && !empty.test(target)) {
                kept.put(region, target);
            }
        });
        return unmodifiableMap(kept);
    }
}
