package com.bbh.itss.dso.portal.domain.discovery;

import java.util.List;

public record DiscoveredStack(
        String buildTool,
        String primaryLanguage,
        String framework,
        String deployTarget,
        String javaVersion,
        String gradleVersion,
        boolean nodeBuildEnabled,
        String nodeVersion,
        String testRunner,
        String unitTestTasks,
        String buildTasks,
        boolean liquibaseDetected,
        boolean dockerDetected,
        List<String> detectedMarkers,
        String summaryMessage
) {
}
