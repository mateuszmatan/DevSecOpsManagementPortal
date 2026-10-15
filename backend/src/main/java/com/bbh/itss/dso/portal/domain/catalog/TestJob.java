package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.Map;
import java.util.regex.Pattern;

import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record TestJob(TestStage stage, String name, TestJobType type, String job, Integer timeoutMinutes,
                      String parameters, String remoteJenkins, String remoteJenkinsUrl, String credentialsId,
                      Integer pollIntervalSec, String tokenCredentialsId, Boolean abortTriggeredJob,
                      Boolean overrideTrustAllCertificates, Boolean preventRemoteBuildQueue,
                      Boolean trustAllCertificates, Boolean useCrumbCache, Boolean useJobInfoCache) {

    private static final Pattern PARAMETER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*=.*$");

    public TestJob {
        name = trimToNull(name);
        job = trim(job);
        parameters = trimToNull(parameters);
        remoteJenkins = trimToNull(remoteJenkins);
        remoteJenkinsUrl = trimToNull(remoteJenkinsUrl);
        credentialsId = trimToNull(credentialsId);
        tokenCredentialsId = trimToNull(tokenCredentialsId);
        abortTriggeredJob = isTrue(abortTriggeredJob);
        overrideTrustAllCertificates = isTrue(overrideTrustAllCertificates);
        preventRemoteBuildQueue = isTrue(preventRemoteBuildQueue);
        trustAllCertificates = isTrue(trustAllCertificates);
        useCrumbCache = isTrue(useCrumbCache);
        useJobInfoCache = isTrue(useJobInfoCache);
    }

    public static TestJob of(TestStage stage, String name, TestJobType type, String job, Integer timeoutMinutes) {
        return builder().stage(stage).name(name).type(type).job(job).timeoutMinutes(timeoutMinutes).build();
    }

    public void validate(ValidationProblems problems) {
        if (needsRemoteJenkins()) {
            problems.add("remoteJenkins", "name the remote Jenkins or its URL, or give the job as a full URL");
        }
        if (parameters != null && parameters.lines().anyMatch(line -> !line.isBlank() && !PARAMETER.matcher(line).matches())) {
            problems.add("parameters", "write one parameter per line as NAME=value");
        }
    }

    public boolean isUrl() {
        return Text.isUrl(job);
    }

    public boolean needsRemoteJenkins() {
        return type == REMOTE && !isUrl() && remoteJenkins == null && remoteJenkinsUrl == null;
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree().set("name", name).set("type", type).set(isUrl() ? "url" : "job", job)
                .set("timeoutMin", timeoutMinutes).set("parameters", parameters).set("remoteJenkins", remoteJenkins)
                .set("remoteJenkinsUrl", remoteJenkinsUrl).set("credentialsId", credentialsId)
                .set("pollIntervalSec", pollIntervalSec).set("tokenCredentialsId", tokenCredentialsId)
                .flag("abortTriggeredJob", abortTriggeredJob)
                .flag("overrideTrustAllCertificates", overrideTrustAllCertificates)
                .flag("preventRemoteBuildQueue", preventRemoteBuildQueue)
                .flag("trustAllCertificates", trustAllCertificates)
                .flag("useCrumbCache", useCrumbCache)
                .flag("useJobInfoCache", useJobInfoCache).toMap();
    }
}
