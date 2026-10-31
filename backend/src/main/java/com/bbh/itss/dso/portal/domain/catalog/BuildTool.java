package com.bbh.itss.dso.portal.domain.catalog;

public enum BuildTool {
    GRADLE, MAVEN, FLUTTER, LIQUIBASE;

    /** Whether the service builds with commands of its own: Gradle tasks or Maven goals. */
    public boolean runsCommands() {
        return this == GRADLE || this == MAVEN;
    }
}
