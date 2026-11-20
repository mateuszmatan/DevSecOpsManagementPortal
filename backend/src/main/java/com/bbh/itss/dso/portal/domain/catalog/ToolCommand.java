package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.ANDROID;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.DOTNET;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.LIQUIBASE;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.SQL;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.NPM;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.trimmed;
import static java.util.Locale.ROOT;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record ToolCommand(List<String> tasks, List<String> flags, String directory, String mavenHome,
                          List<String> environment, String label, Boolean returnStdout) {

    public static final ToolCommand NONE = builder().build();

    public ToolCommand {
        tasks = trimmed(tasks);
        flags = trimmed(flags);
        directory = trimToNull(directory);
        mavenHome = trimToNull(mavenHome);
        environment = trimmed(environment);
        label = trimToNull(label);
        returnStdout = isTrue(returnStdout);
    }

    public static ToolCommand of(List<String> tasks, List<String> flags) {
        return builder().tasks(tasks).flags(flags).build();
    }

    public boolean isEmpty() {
        return tasks.isEmpty() && flags.isEmpty() && directory == null && mavenHome == null && environment.isEmpty()
                && label == null && !returnStdout;
    }

    /**
     * The blocks a service of SQL and PL/SQL files without a changelog never writes: the library runs Liquibase with
     * the changelog it generates, and a build or unit test command would make the service a plain Liquibase service.
     */
    private static final java.util.Set<String> RUN_BY_THE_LIBRARY = java.util.Set.of("build", "tests.unitTests");

    public void writeTo(ConfigTree config, String path, BuildTool tool) {
        if (tool == GRADLE || tool == MAVEN || tool == ANDROID) {
            boolean maven = tool == MAVEN;
            String block = path + (maven ? ".maven." : ".gradle.");
            config.set(block + (maven ? "goals" : "tasks"), tasks)
                    .set(block + "flags", flags)
                    .set(block + "dir", directory)
                    .set(block + "mvnPath", maven ? mavenHome : null)
                    .set(block + "env", environmentMap())
                    .set(block + "label", label)
                    .flag(block + "returnStdout", returnStdout);
        } else if (tool == LIQUIBASE || tool == DOTNET || tool == SQL && !RUN_BY_THE_LIBRARY.contains(path)) {
            String block = path + (tool == DOTNET ? ".dotnet." : ".liquibase.");
            config.set(block + "commands", tasks)
                    .set(block + "flags", flags)
                    .set(block + "dir", directory)
                    .set(block + "env", environmentMap())
                    .set(block + "label", label)
                    .flag(block + "returnStdout", returnStdout);
        } else if (BuildTool.scripted(tool)) {
            writeScripted(config, path, tool);
        }
    }

    /**
     * The keys the library reads for npm, pip and iOS: the build and the unit tests are one command line each, which
     * may chain commands with &&, and the SonarScanner takes arguments only. The build of an npm service is its
     * Node.js build (build.node), so it writes no build command here.
     */
    private void writeScripted(ConfigTree config, String path, BuildTool tool) {
        String name = tool.name().toLowerCase(ROOT);
        List<String> line = tasks.isEmpty() ? List.of() : List.of(String.join(" ", tasks));
        if ("build".equals(path) && tool != NPM) {
            config.set("build." + name + ".commands", line);
        } else if ("tests.unitTests".equals(path)) {
            config.set(path + ".commands", line).set(path + ".dir", directory);
        } else if ("tools.sonar".equals(path)) {
            config.set(path + "." + name + ".flags", flags);
        }
    }

    /**
     * Adds a problem for every part of the command that the library does not read for npm, pip and iOS, so that
     * nothing a user enters is dropped silently.
     */
    public void validateScripted(ValidationProblems problems, String path, BuildTool tool) {
        if (!BuildTool.scripted(tool)) {
            return;
        }
        String name = TOOL_NAMES.get(tool);
        boolean sonar = "tools.sonar".equals(path);
        boolean none = "asoc".equals(path) || "build".equals(path) && tool == NPM;
        if (!tasks.isEmpty() && (sonar || none)) {
            problems.add("tasks", sonar
                    ? "must be empty for " + name + ": the library runs the SonarScanner, which takes only the flags"
                    : "build".equals(path)
                            ? "must be empty for " + name + ": the Node.js build of the service is its build"
                            : "must be empty for " + name + ": the scan needs no compile command");
        }
        if (!flags.isEmpty() && !sonar) {
            problems.add("flags", "must be empty for " + name + ": write the options in the command line");
        }
        if (directory != null && !"tests.unitTests".equals(path)) {
            problems.add("directory", "must be empty for " + name + ": the command runs in the source folder of the service");
        }
        if (!environment.isEmpty() || label != null || returnStdout || mavenHome != null) {
            problems.add("environment", "environment variables, step label, Maven home and returned output are not"
                    + " used for " + name);
        }
    }

    private static final Map<BuildTool, String> TOOL_NAMES = Map.of(NPM, "npm", BuildTool.PIP, "pip (Python)",
            BuildTool.IOS, "iOS");

    public void validate(ValidationProblems problems) {
        LINES_1000.check(problems, "tasks", tasks);
        LINES_2000.check(problems, "flags", flags);
        LINES_4000.check(problems, "environment", environment);
    }

    public Map<String, String> environmentMap() {
        return environmentMap(environment);
    }

    public static Map<String, String> environmentMap(List<String> environment) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String variable : environment) {
            int separator = variable.indexOf('=');
            if (separator > 0) {
                variables.put(variable.substring(0, separator).trim(), variable.substring(separator + 1).trim());
            }
        }
        return variables;
    }
}
