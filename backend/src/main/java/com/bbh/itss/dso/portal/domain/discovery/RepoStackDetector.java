package com.bbh.itss.dso.portal.domain.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepoStackDetector {

    private static final Pattern JAVA_VERSION_POM = Pattern.compile("<(?:java\\.version|maven\\.compiler\\.source|maven\\.compiler\\.target|maven\\.compiler\\.release|jdk\\.version)>\\s*([0-9]+(?:\\.[0-9]+)?)\\s*<");
    private static final Pattern GRADLE_WRAPPER_VERSION = Pattern.compile("gradle-([0-9]+(?:\\.[0-9]+)*)-");
    private static final Pattern GRADLE_JVM_TOOLCHAIN = Pattern.compile("(?:jvmToolchain\\(|JavaLanguageVersion\\.of\\(|sourceCompatibility\\s*=\\s*['\"]?)([0-9]+)");
    private static final Pattern NODE_ENGINE_VERSION = Pattern.compile("\"node\"\\s*:\\s*\"([^\"]+)\"");

    private RepoStackDetector() {
    }

    /**
     * Detects the stack of a repository. The build tool is found in the order of the library
     * (RepoStackAutoResolver): pubspec.yaml, the iOS files, pom.xml, the Gradle files (Android with an
     * AndroidManifest.xml), the .NET files, a Liquibase changelog, the Python files, package.json or angular.json at
     * the root, and at last a repository of only SQL and PL/SQL files; the first that matches wins.
     */
    public static DiscoveredStack detect(RepoFileTree tree) {
        List<String> markers = new ArrayList<>();
        String buildTool = null;
        String language = null;
        String framework = null;
        String deployTarget = "VM";
        String javaVersion = null;
        String gradleVersion = null;
        boolean nodeBuildEnabled = false;
        String nodeVersion = null;
        String testRunner = null;
        String unitTestTasks = null;
        String buildTasks = null;
        boolean liquibaseDetected = false;
        boolean dockerDetected = false;
        String sqlFolder = null;

        if (tree.fileExists("Dockerfile") || tree.fileExists("Containerfile") || tree.directoryExists("openshift")
                || tree.directoryExists("k8s") || tree.directoryExists("helm") || tree.fileExists("deployment.yaml")
                || tree.fileExists("deploy.yaml")) {
            dockerDetected = true;
            deployTarget = "OPENSHIFT";
            if (tree.fileExists("Dockerfile")) {
                markers.add("Dockerfile");
            } else if (tree.fileExists("Containerfile")) {
                markers.add("Containerfile");
            } else if (tree.directoryExists("openshift")) {
                markers.add("openshift/");
            } else if (tree.directoryExists("k8s")) {
                markers.add("k8s/");
            } else if (tree.fileExists("deployment.yaml")) {
                markers.add("deployment.yaml");
            }
        }

        Optional<String> packageJson = findPackageJson(tree);
        if (packageJson.isPresent()) {
            nodeBuildEnabled = true;
            markers.add("package.json");
            String content = packageJson.get();
            Matcher nodeMatcher = NODE_ENGINE_VERSION.matcher(content);
            if (nodeMatcher.find()) {
                nodeVersion = nodeMatcher.group(1).trim();
            }
            if (content.contains("\"cypress\"")) {
                testRunner = "CYPRESS";
            } else if (content.contains("\"jest\"")) {
                testRunner = "JEST";
            }
            if (content.contains("\"@angular/core\"")) {
                framework = "ANGULAR";
                language = "TYPESCRIPT";
            } else if (content.contains("\"react\"")) {
                framework = "REACT";
                language = "JAVASCRIPT";
            }
        }

        if (hasChangelog(tree) || tree.directoryExists("db/changelog")
                || tree.directoryExists("src/main/resources/db/changelog")) {
            liquibaseDetected = true;
            if (tree.fileExists("changelog.xml")) {
                markers.add("changelog.xml");
            } else if (tree.fileExists("liquibase.properties")) {
                markers.add("liquibase.properties");
            } else {
                markers.add("db/changelog/");
            }
        }

        if (tree.fileExists("pubspec.yaml") || tree.fileExists("pubspec.yml")) {
            buildTool = "FLUTTER";
            language = "DART";
            framework = "FLUTTER";
            markers.add("pubspec.yaml");
        } else if (iosMarker(tree) != null) {
            buildTool = "IOS";
            language = "SWIFT";
            framework = "IOS";
            markers.add(iosMarker(tree));
        } else if (tree.fileExists("pom.xml")) {
            buildTool = "MAVEN";
            language = "JAVA";
            markers.add("pom.xml");
            String pom = tree.readFile("pom.xml").orElse("");
            Matcher m = JAVA_VERSION_POM.matcher(pom);
            if (m.find()) {
                javaVersion = normalizeJavaVersion(m.group(1));
            }
            if (pom.contains("kotlin-maven-plugin") || pom.contains("org.jetbrains.kotlin") || hasKotlinFiles(tree)) {
                language = "KOTLIN";
            }
            if (pom.contains("spring-boot")) {
                framework = "SPRING_BOOT";
            }
            boolean hasJacoco = pom.contains("jacoco");
            unitTestTasks = hasJacoco ? "test jacoco:report" : "test";
            buildTasks = "clean verify";
        } else if (GRADLE_MARKERS.stream().anyMatch(tree::fileExists)) {
            boolean android = ANDROID_MARKERS.stream().anyMatch(tree::fileExists);
            buildTool = android ? "ANDROID" : "GRADLE";
            language = "JAVA";
            if (android) {
                framework = "ANDROID";
                markers.add("AndroidManifest.xml");
            }
            if (tree.fileExists("build.gradle.kts")) {
                markers.add("build.gradle.kts");
                language = "KOTLIN";
            } else if (tree.fileExists("build.gradle")) {
                markers.add("build.gradle");
            }
            String gradle = tree.readFile("build.gradle.kts").or(() -> tree.readFile("build.gradle")).orElse("");
            if (gradle.contains("org.jetbrains.kotlin") || gradle.contains("kotlin(\"jvm\")") || hasKotlinFiles(tree)) {
                language = "KOTLIN";
            }
            if (!android && gradle.contains("org.springframework.boot")) {
                framework = "SPRING_BOOT";
            }
            Matcher jvmMatcher = GRADLE_JVM_TOOLCHAIN.matcher(gradle);
            if (jvmMatcher.find()) {
                javaVersion = normalizeJavaVersion(jvmMatcher.group(1));
            }
            Optional<String> wrapper = tree.readFile("gradle/wrapper/gradle-wrapper.properties");
            if (wrapper.isPresent()) {
                markers.add("gradle-wrapper.properties");
                Matcher wrapMatcher = GRADLE_WRAPPER_VERSION.matcher(wrapper.get());
                if (wrapMatcher.find()) {
                    gradleVersion = wrapMatcher.group(1);
                    if (javaVersion == null) {
                        javaVersion = inferJavaFromGradle(gradleVersion);
                    }
                }
            }
            boolean hasJacoco = gradle.contains("jacoco");
            unitTestTasks = hasJacoco ? "test jacocoTestReport" : "test";
            buildTasks = "clean build";
        } else if (dotnetMarker(tree) != null) {
            String marker = dotnetMarker(tree);
            buildTool = "DOTNET";
            language = marker.endsWith(".fsproj") ? "FSHARP" : marker.endsWith(".vbproj") ? "VBNET" : "CSHARP";
            framework = "DOTNET";
            markers.add(marker);
        } else if (hasChangelog(tree)) {
            buildTool = "LIQUIBASE";
            language = "SQL";
            framework = "LIQUIBASE";
            buildTasks = "validate";
            unitTestTasks = "updateSQL validate";
        } else if (tree.fileExists("requirements.txt") || tree.fileExists("pyproject.toml") || tree.fileExists("Pipfile") || tree.fileExists("setup.py")) {
            buildTool = "PIP";
            language = "PYTHON";
            framework = "PIP";
            testRunner = "PYTEST";
            unitTestTasks = "pytest --junitxml=junit.xml";
            if (tree.fileExists("requirements.txt")) {
                markers.add("requirements.txt");
                buildTasks = "pip install -r requirements.txt";
            } else if (tree.fileExists("pyproject.toml")) {
                markers.add("pyproject.toml");
                buildTasks = "pip install .";
            } else {
                markers.add(tree.fileExists("setup.py") ? "setup.py" : "Pipfile");
            }
        } else if (tree.fileExists("package.json") || tree.fileExists("angular.json")) {
            buildTool = "NPM";
            if (tree.fileExists("angular.json")) {
                markers.add("angular.json");
                framework = "ANGULAR";
                language = "TYPESCRIPT";
            }
            if (language == null) {
                language = "JAVASCRIPT";
            }
            unitTestTasks = "npm test";
        } else if (!packageJson.isPresent() && !hasChangelog(tree) && !sqlFiles(tree).isEmpty()) {
            buildTool = "SQL";
            language = "SQL";
            framework = "SQL";
            sqlFolder = sqlFolder(sqlFiles(tree));
            markers.add(sqlFiles(tree).get(0));
        }

        if (buildTool == null && nodeBuildEnabled) {
            if ("ANGULAR".equals(framework) || "REACT".equals(framework)) {
                buildTasks = "npm run build";
                unitTestTasks = "CYPRESS".equals(testRunner) ? "npx cypress run" : "npm test";
            }
        }

        boolean sonarPropertiesDetected = tree.fileExists("sonar-project.properties");
        if (sonarPropertiesDetected) {
            markers.add("sonar-project.properties");
        }

        List<String> sonarModules = detectSonarModules(tree);
        String sonarProjectKey = null;
        String sonarSources = null;
        String sonarTests = null;

        if (!sonarPropertiesDetected) {
            String derivedName = deriveProjectName(tree, buildTool);
            sonarProjectKey = "com.bbh:" + derivedName;
            if ("DART".equals(language)) {
                sonarSources = "lib";
                sonarTests = "test";
            } else if ("SQL".equals(language)) {
                sonarSources = "SQL".equals(buildTool) ? (sqlFolder != null ? sqlFolder : ".") : "db";
                sonarTests = "";
            } else if ("PYTHON".equals(language)) {
                sonarSources = "src";
                sonarTests = "tests";
            } else if ("NPM".equals(buildTool)) {
                sonarSources = "src";
                sonarTests = "";
            } else if ("IOS".equals(buildTool)) {
                sonarSources = ".";
                sonarTests = "";
            } else if ("DOTNET".equals(framework)) {
                sonarSources = ".";
                sonarTests = "";
            } else if ("KOTLIN".equals(language)) {
                sonarSources = "src/main/kotlin";
                sonarTests = "src/test/kotlin";
            } else {
                sonarSources = "src/main/java";
                sonarTests = "src/test/java";
            }
        }

        List<ResolvedParameter> resolved = new ArrayList<>();
        add(resolved, "buildTool", buildTool, buildToolReason(tree, buildTool));
        if ("SQL".equals(buildTool)) {
            add(resolved, "database.searchPath", sqlFolder, "the one top folder that holds every SQL and PL/SQL file");
            add(resolved, "buildToolAutoSetup", "true", "no javaPath - Liquibase runs with the JDK of the auto setup");
        }
        add(resolved, "build.javaVersion", javaVersion, "read from the build files");
        add(resolved, "build.gradle.version", gradleVersion, "distributionUrl of gradle-wrapper.properties");
        add(resolved, "build.node.enabled", nodeBuildEnabled ? "true" : null, "package.json found");
        add(resolved, "build.tasks", buildTasks, "default build command of " + buildTool);
        add(resolved, "tests.unitTests.tasks", unitTestTasks, unitTestTasks != null && unitTestTasks.contains("jacoco") ? "JaCoCo found" : "default test command");
        add(resolved, "tests.unitTests.runner", testRunner, "test framework in package.json");
        add(resolved, "deployTarget", dockerDetected ? deployTarget : null, "container or OpenShift files found");
        add(resolved, "tools.sonar.projectKey", sonarProjectKey, "com.bbh: and the project name");
        add(resolved, "tools.sonar.sources", sonarSources, "default sources of " + language);
        add(resolved, "tools.sonar.tests", sonarTests == null || sonarTests.isEmpty() ? null : sonarTests, "default tests of " + language);

        String summary = buildSummaryMessage(buildTool, language, framework, deployTarget, javaVersion, gradleVersion, nodeBuildEnabled, testRunner, sonarProjectKey, sonarPropertiesDetected);

        return new DiscoveredStack(
                buildTool,
                language,
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
                markers,
                summary,
                sonarPropertiesDetected,
                sonarProjectKey,
                sonarModules,
                sonarSources,
                sonarTests,
                resolved
        );
    }

    private static void add(List<ResolvedParameter> resolved, String field, String value, String reason) {
        if (value != null) {
            resolved.add(new ResolvedParameter(field, value, reason));
        }
    }

    private static String buildToolReason(RepoFileTree tree, String buildTool) {
        if (buildTool == null) {
            return null;
        }
        return switch (buildTool) {
            case "FLUTTER" -> "pubspec.yaml found";
            case "MAVEN" -> "pom.xml found";
            case "GRADLE" -> tree.fileExists("build.gradle.kts") ? "build.gradle.kts found (Kotlin DSL)" : "Gradle build script found";
            case "ANDROID" -> "Gradle build script and AndroidManifest.xml found (Android)";
            case "IOS" -> iosMarker(tree) + " found (iOS)";
            case "NPM" -> tree.fileExists("angular.json") ? "package.json and angular.json found (Angular)" : "package.json found (Node.js)";
            case "DOTNET" -> dotnetMarker(tree) + " found (C# / .NET)";
            case "LIQUIBASE" -> "Liquibase changelog found";
            case "SQL" -> "only SQL and PL/SQL files found, no build file and no Liquibase changelog";
            default -> "Python project files found";
        };
    }

    private static final List<String> GRADLE_MARKERS = List.of("build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "gradle/wrapper/gradle-wrapper.properties");
    private static final List<String> ANDROID_MARKERS = List.of("AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml");

    /** The file or folder that shows an iOS project at the repository root, as the library looks for it. */
    private static String iosMarker(RepoFileTree tree) {
        for (String file : List.of("Podfile", "Package.swift")) {
            if (tree.fileExists(file)) {
                return file;
            }
        }
        for (String extension : List.of(".xcodeproj", ".xcworkspace")) {
            for (String file : tree.listFiles("")) {
                String top = file.contains("/") ? file.substring(0, file.indexOf('/')) : file;
                if (top.endsWith(extension)) {
                    return top;
                }
            }
        }
        return null;
    }

    private static final List<String> CHANGELOGS = List.of("liquibase.properties", "changelog.xml", "changelog.yaml",
            "changelog.yml", "changelog.json", "changelog.sql", "db/changelog.xml", "db/changelog.yaml",
            "db/changelog.sql", "db/changelog/db.changelog-master.xml", "db/changelog/db.changelog-master.yaml",
            "sql/changelog.xml", "sql/changelog.sql", "liquibase/changelog.xml",
            "src/main/resources/db/changelog/db.changelog-master.xml",
            "src/main/resources/db/changelog/db.changelog-master.yaml", "src/main/resources/changelog.xml");
    private static final List<String> SQL_EXTENSIONS = List.of(".sql", ".pks", ".pkb", ".pls", ".plb", ".pck", ".prc",
            ".fnc", ".trg", ".tps", ".tpb", ".typ", ".bdy", ".vw");

    /** Whether the repository has one of the files the library takes for a Liquibase changelog. */
    private static boolean hasChangelog(RepoFileTree tree) {
        return CHANGELOGS.stream().anyMatch(tree::fileExists);
    }

    /** The SQL and PL/SQL files of the repository, as the library lists them: none of a folder whose name starts with a dot. */
    private static List<String> sqlFiles(RepoFileTree tree) {
        return tree.listFiles("").stream()
                .filter(file -> SQL_EXTENSIONS.stream().anyMatch(file.toLowerCase(java.util.Locale.ROOT)::endsWith))
                .filter(file -> java.util.Arrays.stream(file.split("/")).noneMatch(part -> part.startsWith(".")))
                .sorted().toList();
    }

    /** The one top folder that holds every file, which becomes the search folder; null when there is none. */
    private static String sqlFolder(List<String> files) {
        if (files.stream().anyMatch(file -> !file.contains("/"))) {
            return null;
        }
        List<String> tops = files.stream().map(file -> file.substring(0, file.indexOf('/'))).distinct().toList();
        return tops.size() == 1 ? tops.get(0) : null;
    }

    private static String dotnetMarker(RepoFileTree tree) {
        for (String file : List.of("global.json", "Directory.Packages.props", "Directory.Build.props", "packages.config", "build.cake")) {
            if (tree.fileExists(file)) {
                return file;
            }
        }
        List<String> all = tree.listFiles("");
        for (String extension : List.of(".sln", ".csproj", ".fsproj", ".vbproj")) {
            for (String file : all) {
                if (file.endsWith(extension)) {
                    return file;
                }
            }
        }
        return null;
    }

    private static Optional<String> findPackageJson(RepoFileTree tree) {
        if (tree.fileExists("package.json")) {
            return tree.readFile("package.json");
        }
        for (String dir : List.of("frontend", "client", "ui", "web")) {
            String path = dir + "/package.json";
            if (tree.fileExists(path)) {
                return tree.readFile(path);
            }
        }
        return Optional.empty();
    }

    private static boolean hasKotlinFiles(RepoFileTree tree) {
        if (tree.directoryExists("src/main/kotlin")) {
            return true;
        }
        return tree.listFiles("src/main").stream().anyMatch(f -> f.endsWith(".kt"));
    }

    private static String normalizeJavaVersion(String v) {
        if (v == null) {
            return null;
        }
        String clean = v.trim();
        if (clean.startsWith("1.")) {
            return clean.substring(2);
        }
        return clean;
    }

    private static String inferJavaFromGradle(String gradleVersion) {
        if (gradleVersion == null) {
            return "17";
        }
        if (gradleVersion.startsWith("8.")) {
            return "17";
        }
        if (gradleVersion.startsWith("7.")) {
            return "11";
        }
        return "17";
    }

    private static String buildSummaryMessage(String buildTool, String language, String framework, String deployTarget,
                                               String javaVersion, String gradleVersion, boolean node, String testRunner,
                                               String sonarProjectKey, boolean sonarPropertiesDetected) {
        StringBuilder sb = new StringBuilder("Discovered stack: ");
        if (buildTool != null) {
            sb.append(buildTool);
        } else if (node) {
            sb.append("NODEJS");
        } else {
            sb.append("GENERIC");
        }
        if (language != null) {
            sb.append(" (").append(language).append(")");
        }
        if (javaVersion != null) {
            sb.append(", Java ").append(javaVersion);
        }
        if (gradleVersion != null) {
            sb.append(", Gradle ").append(gradleVersion);
        }
        if (node) {
            sb.append(", Node.js Stage 0");
        }
        if (testRunner != null) {
            sb.append(", Runner: ").append(testRunner);
        }
        if (framework != null) {
            sb.append(", Framework: ").append(framework);
        }
        sb.append(", Target: ").append(deployTarget);
        if (!sonarPropertiesDetected && sonarProjectKey != null) {
            sb.append(", Sonar defaults (").append(sonarProjectKey).append(")");
        }
        return sb.toString();
    }

    private static String deriveProjectName(RepoFileTree tree, String buildTool) {
        if ("MAVEN".equals(buildTool) || tree.fileExists("pom.xml")) {
            String pom = tree.readFile("pom.xml").orElse("");
            Matcher m = Pattern.compile("<artifactId>\\s*([^<\\s]+)\\s*<").matcher(pom);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        if (tree.fileExists("settings.gradle") || tree.fileExists("settings.gradle.kts")) {
            String settings = tree.readFile("settings.gradle.kts").or(() -> tree.readFile("settings.gradle")).orElse("");
            Matcher m = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(settings);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        if (tree.fileExists("package.json")) {
            String pkg = tree.readFile("package.json").orElse("");
            Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(pkg);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return "app";
    }

    private static List<String> detectSonarModules(RepoFileTree tree) {
        List<String> modules = new ArrayList<>();
        if (tree.fileExists("pom.xml")) {
            String pom = tree.readFile("pom.xml").orElse("");
            Matcher m = Pattern.compile("<module>\\s*([^<\\s]+)\\s*</module>").matcher(pom);
            while (m.find()) {
                String mod = m.group(1).trim();
                if (!mod.isBlank() && !modules.contains(mod)) {
                    modules.add(mod);
                }
            }
        }
        if (modules.isEmpty() && (tree.fileExists("settings.gradle") || tree.fileExists("settings.gradle.kts"))) {
            String settings = tree.readFile("settings.gradle.kts").or(() -> tree.readFile("settings.gradle")).orElse("");
            Matcher m = Pattern.compile("include\\s*\\(?\\s*['\"](?::)?([^'\",\\s)]+)['\"]").matcher(settings);
            while (m.find()) {
                String mod = m.group(1).trim();
                if (!mod.isBlank() && !modules.contains(mod)) {
                    modules.add(mod);
                }
            }
        }
        return modules;
    }
}
