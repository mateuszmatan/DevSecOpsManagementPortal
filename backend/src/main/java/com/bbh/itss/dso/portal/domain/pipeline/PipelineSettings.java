package com.bbh.itss.dso.portal.domain.pipeline;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.COMMAS_1000;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static com.bbh.itss.dso.portal.domain.shared.Text.isUrl;
import static com.bbh.itss.dso.portal.domain.shared.UriEncoding.decode;
import static com.bbh.itss.dso.portal.domain.shared.UriEncoding.pathSegment;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder(toBuilder = true)
public record PipelineSettings(List<String> agentLabels, String extendedPipelineJob, String securityPipelineJob,
                               String jenkinsJob, String description) {

    public static final String DEFAULT_AGENT_LABEL = "linux-agent";
    public static final int JENKINS_JOB_MAX = 1000;
    public static final int DESCRIPTION_MAX = 1000;

    public PipelineSettings {
        agentLabels = clean(agentLabels);
        extendedPipelineJob = trimToNull(extendedPipelineJob);
        securityPipelineJob = trimToNull(securityPipelineJob);
        jenkinsJob = trimToNull(jenkinsJob);
        description = trimToNull(description);
    }

    public void validate(ValidationProblems problems) {
        problems.require("agentLabels", agentLabels, "add at least one Jenkins agent label");
        COMMAS_1000.check(problems, "agentLabels", agentLabels);
        problems.fits("jenkinsJob", jenkinsJob, JENKINS_JOB_MAX).fits("description", description, DESCRIPTION_MAX);
    }

    public PipelineSettings forType(PipelineType type) {
        return toBuilder().extendedPipelineJob(type == SECURITY ? extendedPipelineJob : null)
                .securityPipelineJob(type == EXTENDED ? securityPipelineJob : null).build();
    }

    public String jenkinsJobUrl(String jenkinsUrl) {
        return jobUrl(jenkinsJob, jenkinsUrl);
    }

    public boolean builds(String recordedJob) {
        String path = jobPath();
        return allNotNull(path, recordedJob) && (recordedJob.equals(path) || recordedJob.startsWith(path + "/"));
    }

    public String jobPath() {
        if (jenkinsJob == null) {
            return null;
        }
        if (!isUrl(jenkinsJob)) {
            return segments(jenkinsJob).collect(joining("/"));
        }
        String[] parts = jenkinsJob.replaceFirst("^https?://[^/]*", "").replaceFirst("[?#].*$", "").split("/");
        List<String> names = new ArrayList<>();
        for (int i = 0; i + 1 < parts.length; i++) {
            if (parts[i].equals("job")) {
                names.add(decode(parts[++i]));
            }
        }
        return names.isEmpty() ? null : String.join("/", names);
    }

    public static String jobUrl(String job, String jenkinsUrl) {
        if (isBlank(job)) {
            return null;
        }
        String trimmed = job.trim();
        if (isUrl(trimmed)) {
            return trimmed;
        }
        if (isBlank(jenkinsUrl)) {
            return null;
        }
        String path = segments(trimmed).map(segment -> "job/" + pathSegment(segment)).collect(joining("/"));
        return stripEnd(trim(jenkinsUrl), "/") + "/" + path + "/";
    }

    private static Stream<String> segments(String path) {
        return Arrays.stream(path.split("/")).map(String::trim).filter(segment -> !segment.isEmpty());
    }
}
