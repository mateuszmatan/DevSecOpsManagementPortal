package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.anyNotNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record UnitTestSettings(ToolCommand command, String resultPattern, String rootDir, String reportOutDir,
                               Boolean allowEmptyResults, String coverageReportPath) {

    public static final UnitTestSettings NONE = builder().build();

    public UnitTestSettings {
        command = getIfNull(command, ToolCommand.NONE);
        resultPattern = trimToNull(resultPattern);
        rootDir = trimToNull(rootDir);
        reportOutDir = trimToNull(reportOutDir);
        allowEmptyResults = isTrue(allowEmptyResults);
        coverageReportPath = trimToNull(coverageReportPath);
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        command.writeTo(config, "tests.unitTests", tool);
        config.set("tests.unitTests.unitTestResult", resultPattern)
                .set("tests.unitTests.rootDir", rootDir)
                .set("tests.unitTests.reportOutDir", reportOutDir)
                .flag("tests.unitTests.allowEmptyResults", allowEmptyResults)
                .set("coverage.reportPath", coverageReportPath);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        boolean configured = !command.isEmpty() || anyNotNull(resultPattern, rootDir, reportOutDir) || allowEmptyResults;
        if (tool != FLUTTER && configured && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == MAVEN
                    ? "add the Maven goals of the unit tests, for example test jacoco:report"
                    : "add the Gradle tasks of the unit tests, for example test jacocoTestReport");
        }
        command.validate(problems.at("command"));
    }
}
