package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.NONE;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record BuildSettings(BuildTool tool, String sourceDir, String javaPath, Boolean autoSetup, String buildPath,
                            ToolCommand command) {

    public static final String DEFAULT_SOURCE_DIR = ".";

    public BuildSettings {
        sourceDir = defaultIfBlank(trim(sourceDir), DEFAULT_SOURCE_DIR);
        javaPath = trimToNull(javaPath);
        autoSetup = isTrue(autoSetup);
        buildPath = trimToNull(buildPath);
        command = getIfNull(command, NONE);
    }

    public void writeTo(ConfigTree config) {
        config.set("buildTool", tool).set("sourceDir", sourceDir).set("javaPath", javaPath)
                .flag("buildToolAutoSetup", autoSetup).set("build.buildPath", buildPath);
        command.writeTo(config, "build", tool);
    }

    public void validate(ValidationProblems problems) {
        if (tool == FLUTTER) {
            validateFlutter(problems);
        } else if (javaPath == null && !autoSetup) {
            problems.add("javaPath", "set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them");
        }
        if (tool != FLUTTER && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == MAVEN
                    ? "add the Maven goals of the build, for example clean verify"
                    : "add the Gradle tasks of the build, for example clean build");
        }
        command.validate(problems.at("command"));
    }

    private void validateFlutter(ValidationProblems problems) {
        problems.require("javaPath", javaPath,
                "is required for Flutter: the build and unit test stages set JAVA_HOME from it");
        if (autoSetup) {
            problems.add("autoSetup", "must be off for Flutter: the automatic build tool setup cannot prepare a Flutter build");
        }
    }
}
