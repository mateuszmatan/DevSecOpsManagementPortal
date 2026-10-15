package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record SonarSettings(String projectName, String projectKey, String installationName, String credentialsId,
                            String authTokenCredentialsId, String badgeToken, Boolean addBadges, Boolean fullBadges,
                            ToolCommand command, String serverUrl) {

    public static final SonarSettings NONE = builder().build();

    public SonarSettings {
        projectName = trimToNull(projectName);
        projectKey = trimToNull(projectKey);
        installationName = trimToNull(installationName);
        credentialsId = trimToNull(credentialsId);
        authTokenCredentialsId = trimToNull(authTokenCredentialsId);
        badgeToken = trimToNull(badgeToken);
        addBadges = isTrue(addBadges);
        fullBadges = isTrue(fullBadges);
        command = getIfNull(command, ToolCommand.NONE);
        serverUrl = trimToNull(serverUrl);
    }

    public static SonarSettings of(String projectName, String projectKey, ToolCommand command) {
        return builder().projectName(projectName).projectKey(projectKey).command(command).build();
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        config.set("tools.sonar.serverUrl", serverUrl)
                .set("tools.sonar.projectName", projectName)
                .set("tools.sonar.projectKey", projectKey)
                .set("tools.sonar.installationName", installationName)
                .set("tools.sonar.credentialsId", credentialsId)
                .set("tools.sonar.authToken", authTokenCredentialsId)
                .set("tools.sonar.badgeToken", badgeToken)
                .flag("tools.sonar.addBadges", addBadges)
                .flag("tools.sonar.fullBadges", fullBadges);
        command.writeTo(config, "tools.sonar", tool);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        if (tool != FLUTTER && (projectKey != null || !command.isEmpty()) && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == MAVEN
                    ? "add the Maven goals of the analysis, for example sonar:sonar"
                    : "add the Gradle tasks of the analysis, for example sonarqube");
        }
        command.validate(problems.at("command"));
    }
}
