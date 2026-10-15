package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.settings.ServiceTemplate;
import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.IMAGE_TAG;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.IMAGE_TAG_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE;

@JsonIgnoreProperties(value = "updatedAt", allowGetters = true)
public record ServiceTemplateDto(
        Long version,
        Instant updatedAt,
        @NotEmpty(message = "add at least one Jenkins agent label") @Size(max = 20)
        List<@Pattern(regexp = "^[^,\\p{Cntrl}]{1,100}$",
                message = "must be a Jenkins label or label expression such as linux && docker, without commas")
                String> agentLabels,
        @Size(max = 500)
        @Pattern(regexp = "^((?!.*\\.\\.)[A-Za-z0-9._ /{}-]+)?$",
                message = "must be a Jenkins job path such as DevSecOps/{CODE}/{service}-{type}, with letters,"
                        + " digits, spaces, . _ / - and placeholders but no '..'")
        String jenkinsJob,
        @Size(max = 500) String gradleTasks,
        @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String gradleArtifact,
        @Size(max = 300) String gradleScanPattern,
        @Size(max = 500) String mavenTasks,
        @Size(max = 500) @Pattern(regexp = SHELL_SAFE, message = SHELL_SAFE_MESSAGE) String mavenArtifact,
        @Size(max = 300) String mavenScanPattern,
        @Size(max = 300) String flutterScanPattern,
        @Size(max = 500) String deliveryTasks,
        @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9._{}-]*$",
                message = "may contain letters, digits, . _ - and placeholders such as {service}")
        String nexusIqApplication,
        @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE) String repositoryUrl,
        @Size(max = 200) String bitbucketCredentialsId,
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9{}-]*$",
                message = "may contain letters, digits, - and placeholders such as {code}; it is written in lower case")
        String openShiftProject,
        @Size(max = 300) @Pattern(regexp = IMAGE_TAG, message = IMAGE_TAG_MESSAGE) String imageRegistry,
        @Size(max = 500) String healthCheckUrl) {

    static ServiceTemplateDto from(StoredServiceTemplate stored) {
        return RecordMapper.map(ServiceTemplateDto.class, stored, stored.template());
    }

    ServiceTemplate toTemplate() {
        return RecordMapper.map(this, ServiceTemplate.class);
    }
}
