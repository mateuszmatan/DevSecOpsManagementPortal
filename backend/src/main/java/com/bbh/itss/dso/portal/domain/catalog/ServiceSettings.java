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
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy.INHERITED;
import static com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget.NONE;
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD;
import static com.bbh.itss.dso.portal.domain.catalog.TestSettings.DEFAULTS;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

@Builder
public record ServiceSettings(BuildSettings build, UnitTestSettings unitTests, TestSettings tests,
                              List<TestJob> testJobs, DeploymentSettings deployment, ToolCommand delivery,
                              UrbanCodeSettings urbanCode, List<UrbanCodeApplicationSettings> urbanCodeApplications,
                              Map<Region, SshTarget> sshTargets, Map<Region, OpenShiftTarget> openShiftTargets,
                              AppScanSettings appScan, SonarSettings sonar, NexusIqSettings nexusIq,
                              List<NexusIqApplication> nexusIqApplications, ScmSettings scm, GoldenFixPolicy goldenFix,
                              @With(PRIVATE) MetricsSettings metrics, FlutterSettings flutter) {

    public ServiceSettings {
        requireNonNull(build, "a service needs its build settings");
        requireNonNull(deployment, "a service needs its deployment settings");
        requireNonNull(appScan, "a service needs its AppScan settings");
        unitTests = getIfNull(unitTests, UnitTestSettings.NONE);
        tests = getIfNull(tests, DEFAULTS);
        testJobs = List.copyOf(emptyIfNull(testJobs));
        delivery = getIfNull(delivery, ToolCommand.NONE);
        urbanCode = getIfNull(urbanCode, UrbanCodeSettings.DEFAULTS);
        urbanCodeApplications = List.copyOf(emptyIfNull(urbanCodeApplications));
        sshTargets = withoutEmpty(sshTargets, SshTarget::isEmpty);
        openShiftTargets = withoutEmpty(openShiftTargets, OpenShiftTarget::isEmpty);
        sonar = getIfNull(sonar, SonarSettings.NONE);
        nexusIq = getIfNull(nexusIq, NexusIqSettings.NONE);
        nexusIqApplications = List.copyOf(emptyIfNull(nexusIqApplications));
        scm = getIfNull(scm, ScmSettings.NONE);
        goldenFix = getIfNull(goldenFix, INHERITED);
        metrics = getIfNull(metrics, MetricsSettings.DEFAULTS);
        flutter = getIfNull(flutter, FlutterSettings.NONE);
    }

    public ServiceSettings withDefaultMetricsProject(String productCode, String serviceName) {
        return withMetrics(metrics.withDefaultProject(productCode, serviceName));
    }

    public void writeTo(ConfigTree config) {
        BuildTool tool = build.tool();
        build.writeTo(config);
        deployment.writeTo(config);
        appScan.writeTo(config, tool);
        metrics.writeTo(config);
        unitTests.writeTo(config, tool);
        sonar.writeTo(config, tool);
        nexusIq.writeTo(config, nexusIqApplications);
        scm.writeTo(config);
        goldenFix.writeTo(config);
        tests.writeTo(config, testJobs);
        if (deployment.target() == VM) {
            if (tool == MAVEN) {
                delivery.writeTo(config, "delivery", MAVEN);
            }
            urbanCode.writeTo(config, urbanCodeApplications);
            sshTargets.forEach((region, target) -> config.set("deploy.vm." + region.configKey(), target.toConfig()));
        } else {
            openShiftTargets.forEach((region, target) ->
                    config.set("deploy.openshift." + region.configKey(), target.toConfig()));
        }
        if (tool == FLUTTER) {
            flutter.writeTo(config);
        }
    }

    public void validate(ValidationProblems problems) {
        BuildTool tool = build.tool();
        DeployTarget target = deployment.target();
        build.validate(problems.at("build"));
        unitTests.validate(problems.at("unitTests"), tool);
        deployment.validate(problems.at("deployment"));
        if (target == OPENSHIFT) {
            openShiftTargets.getOrDefault(RD, NONE)
                    .validateImageBuild(problems.at("openShiftTargets[" + RD.name() + "]"));
        }
        appScan.validate(problems.at("appScan"), tool);
        sonar.validate(problems.at("sonar"), tool);
        nexusIq.validate(problems, nexusIqApplications);
        scm.validate(problems.at("scm"));
        goldenFix.validate(problems.at("goldenFix"));
        if (tool == FLUTTER) {
            flutter.validate(problems.at("flutter"), target);
        }
        if (target == VM && tool == MAVEN) {
            problems.require("build.buildPath", build.buildPath(),
                    "is required for Maven on VMs: the Nexus delivery publishes the artifact found there");
            problems.require("delivery.tasks", delivery.tasks(),
                    "add the Maven goals that upload the snapshot, for example deploy:deploy-file");
        }
        delivery.validate(problems.at("delivery"));
        for (int i = 0; i < testJobs.size(); i++) {
            testJobs.get(i).validate(problems.at("testJobs[" + i + "]"));
        }
        for (int i = 0; i < urbanCodeApplications.size(); i++) {
            urbanCodeApplications.get(i).validate(problems.at("urbanCodeApplications[" + i + "]"));
        }
    }

    private static <T> Map<Region, T> withoutEmpty(Map<Region, T> targets, Predicate<T> empty) {
        if (targets == null) {
            return Map.of();
        }
        Map<Region, T> kept = new EnumMap<>(Region.class);
        targets.forEach((region, target) -> {
            if (region != null && target != null && !empty.test(target)) {
                kept.put(region, target);
            }
        });
        return unmodifiableMap(kept);
    }
}
