package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.NONE;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static java.util.Locale.ROOT;
import static org.apache.commons.lang3.BooleanUtils.isNotFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record AppScanSettings(String applicationId, String sastScanName, List<String> includedDirs,
                              List<String> excludedDirs, Boolean compile, Boolean sourceCodeOnly, Boolean useConfigFile,
                              Boolean insecureTls, String clientPath, ToolCommand compileCommand, Boolean dastEnabled,
                              String dastScanName, String dastTargetUrl, String dastPresenceId,
                              String secretCredentialsId) {

    public AppScanSettings {
        applicationId = lowerCase(trim(applicationId), ROOT);
        sastScanName = trimToNull(sastScanName);
        includedDirs = clean(includedDirs);
        excludedDirs = clean(excludedDirs);
        compile = isNotFalse(compile);
        sourceCodeOnly = isTrue(sourceCodeOnly);
        useConfigFile = isTrue(useConfigFile);
        insecureTls = isTrue(insecureTls);
        clientPath = trimToNull(clientPath);
        compileCommand = getIfNull(compileCommand, NONE);
        dastEnabled = isTrue(dastEnabled);
        dastScanName = trimToNull(dastScanName);
        dastTargetUrl = trimToNull(dastTargetUrl);
        dastPresenceId = trimToNull(dastPresenceId);
        secretCredentialsId = trimToNull(secretCredentialsId);
    }

    public static AppScanSettings of(String applicationId) {
        return builder().applicationId(applicationId).build();
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        config.set("appId", applicationId)
                .set("includedDirs", String.join(",", includedDirs))
                .set("excludedDirs", String.join(",", excludedDirs))
                .set("appscanPath", clientPath)
                .set("asoc.token", secretCredentialsId)
                .set("asoc.doCompile", compile ? null : false)
                .flag("asoc.sourceCodeOnly", sourceCodeOnly)
                .flag("asoc.useAppScanConfig", useConfigFile)
                .flag("asoc.insecureTls", insecureTls);
        compileCommand.writeTo(config, "asoc", tool);
        config.set("sast.scanName", sastScanName)
                .set("dast.enabled", dastEnabled)
                .set("dast.scanName", dastScanName)
                .set("dast.targetUrl", dastTargetUrl)
                .set("dast.presenceId", dastPresenceId);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        if (dastEnabled && dastTargetUrl == null) {
            problems.add("dastTargetUrl", "is required when DAST is enabled");
        }
        if (compile && tool != FLUTTER && !compileCommand.isEmpty() && compileCommand.tasks().isEmpty()) {
            problems.add("compileCommand.tasks", tool == MAVEN
                    ? "add the Maven goals that compile the code for the scan, or clear the command to use the build goals"
                    : "add the Gradle tasks that compile the code for the scan, or clear the command to use the build tasks");
        }
        LINES_2000.check(problems, "includedDirs", includedDirs);
        LINES_2000.check(problems, "excludedDirs", excludedDirs);
        compileCommand.validate(problems.at("compileCommand"));
    }
}
