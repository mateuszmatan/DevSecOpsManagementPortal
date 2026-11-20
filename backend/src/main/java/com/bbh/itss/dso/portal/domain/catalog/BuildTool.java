package com.bbh.itss.dso.portal.domain.catalog;

public enum BuildTool {
    GRADLE, MAVEN, FLUTTER, LIQUIBASE, DOTNET, SQL;

    public static boolean needsTasks(BuildTool tool) {
        return tool != FLUTTER && tool != LIQUIBASE && tool != DOTNET && tool != SQL;
    }

    public static boolean needsJdk(BuildTool tool) {
        return tool != LIQUIBASE && tool != DOTNET && tool != SQL;
    }

    /** Whether the build tool can change a database: Liquibase with a changelog, or SQL and PL/SQL files without one. */
    public static boolean database(BuildTool tool) {
        return tool == LIQUIBASE || tool == SQL;
    }
}
