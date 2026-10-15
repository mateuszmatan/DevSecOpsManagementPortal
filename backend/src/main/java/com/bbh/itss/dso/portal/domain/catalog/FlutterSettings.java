package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record FlutterSettings(FlutterPlatform platform, List<String> modules, List<String> testModules,
                              List<String> testSubmodules, List<String> testSubplugins,
                              String signingPasswordCredentialsId, String prodLicenseCredentialsId,
                              String testLicenseCredentialsId, String deliveryGroup, String deliveryArtifact,
                              String deliveryPlugin, String sonarSources, String sonarTests, Boolean sonarFlutterPlugin,
                              String dartAnalyzeCommand, String sonarScannerVersion) {

    public static final FlutterSettings NONE = builder().build();

    public FlutterSettings {
        modules = clean(modules);
        testModules = clean(testModules);
        testSubmodules = clean(testSubmodules);
        testSubplugins = clean(testSubplugins);
        signingPasswordCredentialsId = trimToNull(signingPasswordCredentialsId);
        prodLicenseCredentialsId = trimToNull(prodLicenseCredentialsId);
        testLicenseCredentialsId = trimToNull(testLicenseCredentialsId);
        deliveryGroup = trimToNull(deliveryGroup);
        deliveryArtifact = trimToNull(deliveryArtifact);
        deliveryPlugin = trimToNull(deliveryPlugin);
        sonarSources = trimToNull(sonarSources);
        sonarTests = trimToNull(sonarTests);
        sonarFlutterPlugin = isTrue(sonarFlutterPlugin);
        dartAnalyzeCommand = trimToNull(dartAnalyzeCommand);
        sonarScannerVersion = trimToNull(sonarScannerVersion);
    }

    public void writeTo(ConfigTree config) {
        config.set("flutter.platform", platform)
                .set("tools.flutter.flutterModules", modules)
                .set("tests.modules", testModules)
                .set("tests.submodules", testSubmodules)
                .set("tests.subplugins", testSubplugins);
        if (allNotNull(signingPasswordCredentialsId, prodLicenseCredentialsId, testLicenseCredentialsId)) {
            config.set("build.credentialsId", List.of(signingPasswordCredentialsId, prodLicenseCredentialsId,
                    testLicenseCredentialsId));
        }
        config.set("delivery.group", deliveryGroup)
                .set("delivery.artifact", deliveryArtifact)
                .set("delivery.plugin", deliveryPlugin)
                .set("tools.sonar.sources", sonarSources)
                .set("tools.sonar.tests", sonarTests)
                .set("tools.sonar.dartAnalyzeCommand", dartAnalyzeCommand)
                .set("tools.sonar.sonarScannerVersion", sonarScannerVersion)
                .flag("tools.sonar.flutterPlugin", sonarFlutterPlugin);
    }

    public void validate(ValidationProblems problems, DeployTarget target) {
        problems.require("modules", modules, "add at least one module: the build stage prepares each of them");
        problems.require("testModules", testModules, "add at least one test module: the unit tests stage runs them");
        LINES_1000.check(problems, "modules", modules);
        LINES_1000.check(problems, "testModules", testModules);
        LINES_1000.check(problems, "testSubmodules", testSubmodules);
        LINES_1000.check(problems, "testSubplugins", testSubplugins);
        String message = "is required: the Flutter build stage reads this Jenkins credential";
        problems.require("signingPasswordCredentialsId", signingPasswordCredentialsId, message)
                .require("prodLicenseCredentialsId", prodLicenseCredentialsId, message)
                .require("testLicenseCredentialsId", testLicenseCredentialsId, message);
        if (target == VM) {
            String delivery = "is required for Flutter on VMs: the Nexus delivery uploads the build under it";
            problems.require("deliveryGroup", deliveryGroup, delivery).require("deliveryArtifact", deliveryArtifact, delivery)
                    .require("deliveryPlugin", deliveryPlugin, delivery);
        }
    }
}
