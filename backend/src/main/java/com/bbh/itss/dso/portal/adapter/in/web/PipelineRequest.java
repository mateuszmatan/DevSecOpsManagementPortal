package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.AGENT_LABEL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.AGENT_LABELS_REQUIRED;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.AGENT_LABEL_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.JOB_PATH;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.JOB_PATH_MESSAGE;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings.JENKINS_JOB_MAX;

public record PipelineRequest(
        @NotNull PipelineType type,
        @NotEmpty(message = AGENT_LABELS_REQUIRED) @Size(max = 20)
        List<@Pattern(regexp = AGENT_LABEL, message = AGENT_LABEL_MESSAGE) String> agentLabels,
        @Size(max = 500) @Pattern(regexp = JOB_PATH, message = JOB_PATH_MESSAGE) String extendedPipelineJob,
        @Size(max = 500) @Pattern(regexp = JOB_PATH, message = JOB_PATH_MESSAGE) String securityPipelineJob,
        @Size(max = JENKINS_JOB_MAX)
        @Pattern(regexp = "^(https?://\\S+|[^\\s:?#][^:?#]*)?$",
                message = "must be a job path such as DevSecOps/CertScanner-gui or the job's http or https URL")
        String jenkinsJob,
        @Size(max = DESCRIPTION_MAX) String description,
        Long version) {

    PipelineSettings toSettings() {
        return new PipelineSettings(agentLabels, extendedPipelineJob, securityPipelineJob, jenkinsJob, description);
    }
}
