package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings.DEFAULT_AGENT_LABEL;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings.JENKINS_JOB_MAX;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.COMMAS_1000;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static java.util.Comparator.comparingInt;
import static java.util.Locale.ROOT;
import static java.util.regex.Matcher.quoteReplacement;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.repeat;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder(toBuilder = true)
public record ServiceTemplate(List<String> agentLabels, String jenkinsJob, String gradleTasks, String gradleArtifact,
                              String gradleScanPattern, String mavenTasks, String mavenArtifact,
                              String mavenScanPattern, String flutterScanPattern, String deliveryTasks,
                              String nexusIqApplication, String repositoryUrl, String bitbucketCredentialsId,
                              String openShiftProject, String imageRegistry, String healthCheckUrl) {

    public static final List<String> SERVICE_PLACEHOLDERS = List.of("CODE", "code", "service");
    public static final List<String> JOB_PLACEHOLDERS = List.of("CODE", "code", "service", "type");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
    private static final String LONGEST_CODE = repeat('X', 50);
    private static final String LONGEST_SERVICE = repeat('x', 100);
    private static final String LONGEST_TYPE = Arrays.stream(PipelineType.values()).map(PipelineType::variant)
            .max(comparingInt(String::length)).orElseThrow();

    public ServiceTemplate {
        agentLabels = clean(agentLabels);
        jenkinsJob = trimToNull(jenkinsJob);
        gradleTasks = trimToNull(gradleTasks);
        gradleArtifact = trimToNull(gradleArtifact);
        gradleScanPattern = trimToNull(gradleScanPattern);
        mavenTasks = trimToNull(mavenTasks);
        mavenArtifact = trimToNull(mavenArtifact);
        mavenScanPattern = trimToNull(mavenScanPattern);
        flutterScanPattern = trimToNull(flutterScanPattern);
        deliveryTasks = trimToNull(deliveryTasks);
        nexusIqApplication = trimToNull(nexusIqApplication);
        repositoryUrl = trimToNull(repositoryUrl);
        bitbucketCredentialsId = trimToNull(bitbucketCredentialsId);
        openShiftProject = trimToNull(openShiftProject);
        imageRegistry = trimToNull(imageRegistry);
        healthCheckUrl = trimToNull(healthCheckUrl);
    }

    public static ServiceTemplate bbhDefaults() {
        return builder().agentLabels(List.of(DEFAULT_AGENT_LABEL)).jenkinsJob("DevSecOps/{CODE}/{service}-{type}")
                .gradleTasks("clean build").gradleArtifact("build/libs/*.jar").gradleScanPattern("**/build/libs/*.jar")
                .mavenTasks("clean verify").mavenArtifact("target/*.jar").mavenScanPattern("**/target/*.jar")
                .flutterScanPattern("**/pubspec.lock").deliveryTasks("deploy:deploy-file")
                .nexusIqApplication("{code}-{service}")
                .repositoryUrl("https://bitbucket.bbh.com/projects/{CODE}/repos/{code}-{service}")
                .bitbucketCredentialsId("bitbucket-http-credentials").openShiftProject("{code}-{service}")
                .imageRegistry("docker-qc.tools.bbh.com").healthCheckUrl("/actuator/health").build();
    }

    public void validate(ValidationProblems problems) {
        problems.require("agentLabels", agentLabels, "add at least one Jenkins agent label");
        COMMAS_1000.check(problems, "agentLabels", agentLabels);
        checkPattern(problems, "jenkinsJob", jenkinsJob, JOB_PLACEHOLDERS, JENKINS_JOB_MAX);
        checkPattern(problems, "nexusIqApplication", nexusIqApplication, SERVICE_PLACEHOLDERS, 200);
        checkPattern(problems, "repositoryUrl", repositoryUrl, SERVICE_PLACEHOLDERS, 1000);
        checkPattern(problems, "openShiftProject", openShiftProject, SERVICE_PLACEHOLDERS, 194);
    }

    public PipelineSettings pipelineSettings(String productCode, String serviceName, PipelineType type) {
        return PipelineSettings.builder().agentLabels(agentLabels)
                .jenkinsJob(fill(jenkinsJob, productCode, serviceName, type.variant())).build();
    }

    public static String fill(String pattern, String productCode, String serviceName, String type) {
        if (pattern == null) {
            return null;
        }
        Map<String, String> values = Map.of("CODE", productCode, "code", productCode.toLowerCase(ROOT),
                "service", serviceName, "type", type);
        Matcher placeholder = PLACEHOLDER.matcher(pattern);
        StringBuilder filled = new StringBuilder();
        while (placeholder.find()) {
            String name = placeholder.group(1);
            placeholder.appendReplacement(filled, quoteReplacement(values.getOrDefault(name, placeholder.group())));
        }
        return placeholder.appendTail(filled).toString();
    }

    private static void checkPattern(ValidationProblems problems, String field, String pattern, List<String> known,
                                     int maxBytes) {
        if (pattern == null) {
            return;
        }
        List<String> unknown = PLACEHOLDER.matcher(pattern).results().map(match -> match.group(1))
                .filter(name -> !known.contains(name)).distinct().toList();
        if (!unknown.isEmpty()) {
            problems.add(field, "knows no placeholder " + braced(unknown) + ": use " + braced(known));
        } else if (bytes(fill(pattern, LONGEST_CODE, LONGEST_SERVICE, LONGEST_TYPE)) > maxBytes) {
            problems.add(field, "is too long once the longest product code and service name are filled in: it may"
                    + " take at most " + maxBytes + " bytes");
        }
    }

    private static String braced(List<String> names) {
        return names.stream().map(name -> "{" + name + "}").collect(joining(", "));
    }
}
