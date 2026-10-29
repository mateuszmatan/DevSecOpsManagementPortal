package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.environmentMap;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.trimmed;
import static java.lang.Boolean.FALSE;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The optional Node.js build the library runs as stage 0, before the Gradle or Maven build. Nothing is written unless
 * it is enabled; the Nexus IQ scan of its lock file is on by default, so only its switching off is kept.
 */
@Builder
public record NodeBuildSettings(Boolean enabled, String directory, List<String> commands, String installationName,
                                String nodePath, List<String> environment, String registry, String npmrcCredentialsId,
                                Integer timeoutMinutes, Boolean nexusIqScan) {

    public static final NodeBuildSettings NONE = builder().build();

    public NodeBuildSettings {
        enabled = isTrue(enabled);
        directory = trimToNull(directory);
        commands = trimmed(commands);
        installationName = trimToNull(installationName);
        nodePath = trimToNull(nodePath);
        environment = trimmed(environment);
        registry = trimToNull(registry);
        npmrcCredentialsId = trimToNull(npmrcCredentialsId);
        nexusIqScan = FALSE.equals(nexusIqScan) ? FALSE : null;
    }

    public void writeTo(ConfigTree config) {
        if (!enabled) {
            return;
        }
        config.set("build.node.enabled", true)
                .set("build.node.dir", directory)
                .set("build.node.commands", commands)
                .set("build.node.installationName", installationName)
                .set("build.node.nodePath", nodePath)
                .set("build.node.env", environmentMap(environment))
                .set("build.node.registry", registry)
                .set("build.node.npmrcCredentialsId", npmrcCredentialsId)
                .set("build.node.timeoutMin", timeoutMinutes)
                .set("build.node.nexusIqScan", nexusIqScan);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        LINES_4000.check(problems, "commands", commands);
        LINES_2000.check(problems, "environment", environment);
        if (!enabled) {
            return;
        }
        if (tool == FLUTTER) {
            problems.add("enabled",
                    "must be off for Flutter: a Flutter build has no Gradle or Maven stage for the Node.js build to run before");
        }
        if (installationName != null && nodePath != null) {
            problems.add("nodePath", "give the Node.js installation or its home on the agent, not both");
        }
    }
}
