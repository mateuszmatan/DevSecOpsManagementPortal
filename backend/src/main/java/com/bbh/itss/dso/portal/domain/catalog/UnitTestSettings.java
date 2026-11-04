package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.LIQUIBASE;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.anyNotNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record UnitTestSettings(ToolCommand command, String resultPattern, String rootDir, String reportOutDir,
                               Boolean allowEmptyResults, String coverageReportPath,
                               Boolean cypressEnabled, String cypressCommand, String cypressReportsDir) {

    public static final UnitTestSettings NONE = builder().build();

    public UnitTestSettings {
        command = getIfNull(command, ToolCommand.NONE);
        resultPattern = trimToNull(resultPattern);
        rootDir = trimToNull(rootDir);
        reportOutDir = trimToNull(reportOutDir);
        allowEmptyResults = isTrue(allowEmptyResults);
        coverageReportPath = trimToNull(coverageReportPath);
        cypressEnabled = isTrue(cypressEnabled);
        cypressCommand = trimToNull(cypressCommand);
        cypressReportsDir = trimToNull(cypressReportsDir);
    }

    public UnitTestSettings(ToolCommand command, String resultPattern, String rootDir, String reportOutDir,
                            Boolean allowEmptyResults, String coverageReportPath) {
        this(command, resultPattern, rootDir, reportOutDir, allowEmptyResults, coverageReportPath, false, null, null);
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        command.writeTo(config, "tests.unitTests", tool);
        config.set("tests.unitTests.unitTestResult", resultPattern)
                .set("tests.unitTests.rootDir", rootDir)
                .set("tests.unitTests.reportOutDir", reportOutDir)
                .flag("tests.unitTests.allowEmptyResults", allowEmptyResults)
                .set("coverage.reportPath", coverageReportPath);
        if (isTrue(cypressEnabled)) {
            config.set("tests.unitTests.cypress.enabled", true)
                    .set("tests.unitTests.cypress.command", cypressCommand)
                    .set("tests.unitTests.cypress.reportsDir", cypressReportsDir);
        }
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        boolean configured = !command.isEmpty() || anyNotNull(resultPattern, rootDir, reportOutDir) || allowEmptyResults || isTrue(cypressEnabled);
        if (tool != FLUTTER && tool != LIQUIBASE && configured && !isTrue(cypressEnabled) && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == MAVEN
                    ? "add the Maven goals of the unit tests, for example test jacoco:report"
                    : "add the Gradle tasks of the unit tests, for example test jacocoTestReport");
        }
        command.validate(problems.at("command"));
    }
}
