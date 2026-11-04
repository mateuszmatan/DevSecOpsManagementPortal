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

        if (tree.fileExists("changelog.xml") || tree.fileExists("liquibase.properties")
                || tree.directoryExists("db/changelog") || tree.directoryExists("src/main/resources/db/changelog")) {
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
        } else if (tree.fileExists("build.gradle") || tree.fileExists("build.gradle.kts") || tree.fileExists("gradlew")) {
            buildTool = "GRADLE";
            language = "JAVA";
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
            if (gradle.contains("org.springframework.boot")) {
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
        } else if (liquibaseDetected) {
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
            unitTestTasks = "pytest";
            if (tree.fileExists("requirements.txt")) {
                markers.add("requirements.txt");
                buildTasks = "pip install -r requirements.txt";
            } else if (tree.fileExists("pyproject.toml")) {
                markers.add("pyproject.toml");
                buildTasks = "pip install .";
            }
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
                sonarSources = "db";
                sonarTests = "";
            } else if ("PYTHON".equals(language)) {
                sonarSources = "src";
                sonarTests = "tests";
            } else if ("KOTLIN".equals(language)) {
                sonarSources = "src/main/kotlin";
                sonarTests = "src/test/kotlin";
            } else {
                sonarSources = "src/main/java";
                sonarTests = "src/test/java";
            }
        }

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
                sonarTests
        );
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
