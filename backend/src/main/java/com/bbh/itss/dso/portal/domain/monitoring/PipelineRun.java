package com.bbh.itss.dso.portal.domain.monitoring;

import java.time.Instant;

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings.jobUrl;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.trim;

public record PipelineRun(Instant time, RunResult result, String branch, Long build, Long durationSeconds,
                          String commit, String job, Long stagesTotal, Long passed, Long warned, Long failed,
                          Long blocked, Long skipped) {

    public PipelineRun {
        requireNonNull(time, "a run has the time it finished");
    }

    public String buildUrl(String jenkinsUrl, String configuredJob) {
        if (build == null) {
            return null;
        }
        String url = getIfNull(jobUrl(job, jenkinsUrl), () -> jobUrl(configuredJob, jenkinsUrl));
        return url == null ? null : stripEnd(trim(url), "/") + "/" + build + "/";
    }
}
