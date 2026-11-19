package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.dso.portal.domain.shared.Text.trimmed;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The Nexus snapshot delivery of a service on virtual machines beyond its upload command: the files of the upload
 * (delivery.push), the Maven coordinates and repository of a Gradle upload, and the download of the extended pipeline
 * (delivery.pull). Each artifact is a line of a glob, for the download a file name, optionally followed by its
 * classifier.
 */
@Builder
public record NexusDeliverySettings(List<String> artifacts, String group, String artifact, String repositoryUrl,
                                    Boolean allowInsecureProtocol, ToolCommand pull, List<String> pullArtifacts,
                                    String pullOutputDirectory) {

    public static final NexusDeliverySettings NONE = builder().build();
    public static final String GRADLE_PUSH_TASK = "publishNexusDeliveryPublicationToNexusRepository";
    public static final String GRADLE_PULL_TASK = "pullNexusArtifacts";
    public static final String DEFAULT_OUTPUT_DIRECTORY = "downloaded-artifacts";

    private static final String COORDINATE = "[A-Za-z0-9_.-]+";
    private static final String EXTENSION = "[A-Za-z0-9_-]+";
    private static final List<String> RESERVED_GRADLE_FLAGS = List.of("-p", "-b", "-x", "-m", "--dry-run");
    private static final List<String> RESERVED_GRADLE_PREFIXES = List.of("-p=", "--project-dir", "--build-file",
            "-PnexusManifest", "--exclude-task");

    public NexusDeliverySettings {
        artifacts = trimmed(artifacts);
        group = trimToNull(group);
        artifact = trimToNull(artifact);
        repositoryUrl = trimToNull(repositoryUrl);
        allowInsecureProtocol = isTrue(allowInsecureProtocol);
        pull = getIfNull(pull, ToolCommand.NONE);
        pullArtifacts = trimmed(pullArtifacts);
        pullOutputDirectory = trimToNull(pullOutputDirectory);
    }

    /** Whether the extended pipeline downloads the snapshot: the files to download are listed. */
    public boolean pulls() {
        return !pullArtifacts.isEmpty();
    }

    /**
     * Renders the delivery of a service on virtual machines. The upload command is delivery.push.maven with the
     * artifacts of a Maven service, delivery.maven without them; a Gradle service passes its flags and environment
     * variables to the upload and the download.
     */
    public void writeTo(ConfigTree config, BuildTool tool, ToolCommand upload) {
        if (tool == MAVEN && artifacts.isEmpty()) {
            upload.writeTo(config, "delivery", MAVEN);
        } else if (tool == MAVEN) {
            upload.writeTo(config, "delivery.push", MAVEN);
            config.set("delivery.push.maven.artifacts", entries(artifacts));
        } else if (tool == GRADLE && !artifacts.isEmpty()) {
            config.set("delivery.push.gradle.group", group).set("delivery.push.gradle.artifact", artifact);
            writeGradle(config, "delivery.push.gradle", upload, artifacts);
        }
        if (pulls() && (tool == MAVEN || tool == GRADLE)) {
            config.set("delivery.pull.outputDirectory", pullOutputDirectory)
                    .set("delivery.pull.group", group)
                    .set("delivery.pull.artifact", artifact);
            if (tool == MAVEN) {
                pull.writeTo(config, "delivery.pull", MAVEN);
                config.set("delivery.pull.maven.artifacts", entries(pullArtifacts));
            } else {
                writeGradle(config, "delivery.pull.gradle", pull, pullArtifacts);
            }
        }
    }

    private void writeGradle(ConfigTree config, String block, ToolCommand command, List<String> files) {
        config.set(block + ".repositoryUrl", repositoryUrl)
                .flag(block + ".allowInsecureProtocol", allowInsecureProtocol)
                .set(block + ".flags", command.flags())
                .set(block + ".env", command.environmentMap())
                .set(block + ".artifacts", entries(files));
    }

    /**
     * The rules of the delivery of a service that publishes to Nexus from virtual machines. The upload command is
     * checked under its own path, as the Gradle upload takes only its flags and environment variables.
     */
    public void validate(ValidationProblems problems, ValidationProblems command, BuildTool tool, ToolCommand upload) {
        LINES_2000.check(problems, "artifacts", artifacts);
        LINES_2000.check(problems, "pullArtifacts", pullArtifacts);
        pull.validate(problems.at("pull"));
        checkEntries(problems, "artifacts", artifacts, false);
        checkEntries(problems, "pullArtifacts", pullArtifacts, true);
        if (group != null && !group.matches(COORDINATE)) {
            problems.add("group", "has letters, digits, '.', '_' and '-' only");
        }
        if (artifact != null && !artifact.matches(COORDINATE)) {
            problems.add("artifact", "has letters, digits, '.', '_' and '-' only");
        }
        if (repositoryUrl != null && !repositoryUrl.matches("https?://\\S+")) {
            problems.add("repositoryUrl", "must be an http or https address");
        }
        if (pullOutputDirectory != null && pullOutputDirectory.contains("'")) {
            problems.add("pullOutputDirectory", "must be a folder without quotes");
        }
        if (tool == GRADLE) {
            validateGradle(problems, command, upload);
        } else if (tool == MAVEN && pulls()) {
            requireCoordinates(problems, "to download the Maven artifacts");
            problems.at("pull").require("tasks", pull.tasks(), "add the Maven goals that download the snapshot, for"
                    + " example org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get");
        }
    }

    private void validateGradle(ValidationProblems problems, ValidationProblems command, ToolCommand upload) {
        if (!artifacts.isEmpty()) {
            requireCoordinates(problems, "to upload the Gradle artifacts");
            problems.require("repositoryUrl", repositoryUrl, "is required to upload the Gradle artifacts");
        }
        if (pulls()) {
            requireCoordinates(problems, "to download the Gradle artifacts");
            problems.require("repositoryUrl", repositoryUrl, "is required to download the Gradle artifacts");
        }
        checkGradleCommand(command, upload, GRADLE_PUSH_TASK);
        checkGradleCommand(problems.at("pull"), pull, GRADLE_PULL_TASK);
    }

    private void requireCoordinates(ValidationProblems problems, String purpose) {
        problems.require("group", group, "is required " + purpose)
                .require("artifact", artifact, "is required " + purpose);
    }

    private static void checkGradleCommand(ValidationProblems problems, ToolCommand command, String task) {
        if (!command.tasks().isEmpty() && !command.tasks().equals(List.of(task))) {
            problems.add("tasks", "the Gradle step of the Nexus delivery runs only the task " + task);
        }
        reservedGradleFlag(command.flags()).ifPresent(flag -> problems.add("flags",
                "the flag " + flag + " is set by the Nexus delivery and cannot be given here"));
    }

    static Optional<String> reservedGradleFlag(List<String> flags) {
        return flags.stream().filter(flag -> RESERVED_GRADLE_FLAGS.contains(flag)
                || RESERVED_GRADLE_PREFIXES.stream().anyMatch(flag::startsWith)).findFirst();
    }

    /**
     * Checks each line: a path (a file name when downloading) with an optional classifier. A downloaded file needs an
     * extension, and two downloads may not share a file name.
     */
    private static void checkEntries(ValidationProblems problems, String field, List<String> lines, boolean names) {
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            String path = parts[0];
            if (parts.length > 2) {
                problems.add(field, "'" + line + "': give a path and at most one classifier, separated by a space");
                return;
            }
            if (path.contains(",") || path.contains("'")) {
                problems.add(field, "'" + path + "' has a comma or a quote, which Maven cannot upload");
                return;
            }
            if (parts.length == 2 && !parts[1].matches(COORDINATE)) {
                problems.add(field, "the classifier '" + parts[1] + "' has letters, digits, '.', '_' and '-' only");
                return;
            }
            if (names) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                int dot = name.lastIndexOf('.');
                if (dot <= 0 || dot == name.length() - 1 || !name.substring(dot + 1).matches(EXTENSION)) {
                    problems.add(field, "'" + name + "' needs a file extension of letters, digits, '_' and '-'");
                    return;
                }
                if (parts.length == 1 && !name.substring(0, dot).matches(COORDINATE)) {
                    problems.add(field, "'" + name + "' is also its classifier, which has letters, digits, '.', '_'"
                            + " and '-' only: add a classifier after the name");
                    return;
                }
                if (!seen.add(name)) {
                    problems.add(field, "two artifacts would be downloaded as " + name);
                    return;
                }
            }
        }
    }

    /** The artifacts as the library reads them: a path and, when the line names one, a classifier. */
    private static List<Map<String, String>> entries(List<String> lines) {
        List<Map<String, String>> entries = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("path", parts[0]);
            if (parts.length > 1) {
                entry.put("classifier", parts[1]);
            }
            entries.add(entry);
        }
        return entries;
    }
}
