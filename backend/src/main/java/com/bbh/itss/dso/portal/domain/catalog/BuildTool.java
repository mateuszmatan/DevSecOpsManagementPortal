package com.bbh.itss.dso.portal.domain.catalog;

public enum BuildTool {
    GRADLE, MAVEN, FLUTTER, LIQUIBASE, DOTNET;

    public static boolean needsTasks(BuildTool tool) {
        return tool != FLUTTER && tool != LIQUIBASE && tool != DOTNET;
    }

    public static boolean needsJdk(BuildTool tool) {
        return tool != LIQUIBASE && tool != DOTNET;
    }
}
