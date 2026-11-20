package com.bbh.itss.dso.portal.domain.catalog;

public enum BuildTool {
    GRADLE, MAVEN, FLUTTER, LIQUIBASE, DOTNET, SQL, NPM, PIP, ANDROID, IOS;

    public static boolean needsTasks(BuildTool tool) {
        return tool == GRADLE || tool == MAVEN || tool == ANDROID;
    }

    public static boolean needsJdk(BuildTool tool) {
        return tool != LIQUIBASE && tool != DOTNET && tool != SQL && !scripted(tool);
    }

    /** Whether the build tool can change a database: Liquibase with a changelog, or SQL and PL/SQL files without one. */
    public static boolean database(BuildTool tool) {
        return tool == LIQUIBASE || tool == SQL;
    }

    /**
     * Whether the library runs whole command lines for the build and the unit tests of the tool, and the SonarScanner
     * for its analysis: npm (Angular, Node.js), pip (Python) and iOS.
     */
    public static boolean scripted(BuildTool tool) {
        return tool == NPM || tool == PIP || tool == IOS;
    }

    /** The tool whose command blocks the service writes: an Android service builds with the Gradle wrapper. */
    public static BuildTool commandsOf(BuildTool tool) {
        return tool == ANDROID ? GRADLE : tool == SQL ? LIQUIBASE : tool;
    }
}
