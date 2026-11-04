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
        String summaryMessage,
        boolean sonarPropertiesDetected,
        String sonarProjectKey,
        List<String> sonarModules,
        String sonarSources,
        String sonarTests
) {
    public DiscoveredStack(
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
        this(
                buildTool,
                primaryLanguage,
                framework,
                deployTarget,
                javaVersion,
                gradleVersion,
                nodeBuildEnabled,
                nodeVersion,
                testRunner,
                unitTestTasks,
                buildTasks,
                liquibaseDetected,
                dockerDetected,
                detectedMarkers,
                summaryMessage,
                false,
                null,
                List.of(),
                null,
                null
        );
    }
}
