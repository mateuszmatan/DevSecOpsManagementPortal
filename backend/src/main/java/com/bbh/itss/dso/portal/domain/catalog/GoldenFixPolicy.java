package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.With;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record GoldenFixPolicy(@With(PRIVATE) Boolean enabled, Integer minThreatLevel,
                              List<String> ecosystems, List<String> goldenVersionTypes, List<String> excludeDirs,
                              Integer verifyMaxAttempts, Integer verifyTimeoutMinutes,
                              String verifyMavenCommand, String verifyGradleCommand, String verifyNpmCommand,
                              String verifyPipCommand, String verifyPubCommand, String verifyNugetCommand, String commitAuthorName,
                              String commitAuthorEmail, String timeZone) {

    public static final GoldenFixPolicy INHERITED = inherit(null);

    public GoldenFixPolicy {
        ecosystems = clean(ecosystems);
        goldenVersionTypes = clean(goldenVersionTypes);
        excludeDirs = clean(excludeDirs);
        verifyMavenCommand = trimToNull(verifyMavenCommand);
        verifyGradleCommand = trimToNull(verifyGradleCommand);
        verifyNpmCommand = trimToNull(verifyNpmCommand);
        verifyPipCommand = trimToNull(verifyPipCommand);
        verifyPubCommand = trimToNull(verifyPubCommand);
        verifyNugetCommand = trimToNull(verifyNugetCommand);
        commitAuthorName = trimToNull(commitAuthorName);
        commitAuthorEmail = trimToNull(commitAuthorEmail);
        timeZone = trimToNull(timeZone);
    }

    public static GoldenFixPolicy inherit(Boolean enabled) {
        return builder().enabled(enabled).build();
    }

    public void writeTo(ConfigTree config) {
        config.set("goldenFix.enabled", enabled)
                .set("goldenFix.minThreatLevel", minThreatLevel)
                .set("goldenFix.ecosystems", ecosystems)
                .set("goldenFix.goldenVersionTypes", goldenVersionTypes)
                .set("goldenFix.excludeDirs", excludeDirs)
                .set("goldenFix.verify.maxAttempts", verifyMaxAttempts)
                .set("goldenFix.verify.timeoutMinutes", verifyTimeoutMinutes)
                .set("goldenFix.verify.commands.maven", verifyMavenCommand)
                .set("goldenFix.verify.commands.gradle", verifyGradleCommand)
                .set("goldenFix.verify.commands.npm", verifyNpmCommand)
                .set("goldenFix.verify.commands.pip", verifyPipCommand)
                .set("goldenFix.verify.commands.pub", verifyPubCommand)
                .set("goldenFix.verify.commands.nuget", verifyNugetCommand)
                .set("goldenFix.commitAuthorName", commitAuthorName)
                .set("goldenFix.commitAuthorEmail", commitAuthorEmail)
                .set("goldenFix.timeZone", timeZone);
    }

    public GoldenFixPolicy enabledByDefault() {
        return enabled != null ? this : withEnabled(true);
    }

    public void validate(ValidationProblems problems) {
        LINES_1000.check(problems, "goldenVersionTypes", goldenVersionTypes);
        LINES_2000.check(problems, "excludeDirs", excludeDirs);
    }

    public void validateComplete(ValidationProblems problems) {
        String message = "is required in the global settings";
        problems.require("minThreatLevel", minThreatLevel, message)
                .require("verifyMaxAttempts", verifyMaxAttempts, message)
                .require("verifyTimeoutMinutes", verifyTimeoutMinutes, message)
                .require("commitAuthorName", commitAuthorName, message)
                .require("commitAuthorEmail", commitAuthorEmail, message)
                .require("ecosystems", ecosystems, "select at least one ecosystem")
                .require("goldenVersionTypes", goldenVersionTypes, "add at least one remediation type");
        validate(problems);
    }
}
