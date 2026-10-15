package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record NexusIqSettings(String serverUrl, String credentialsId, String scaScanName) {

    public static final NexusIqSettings NONE = new NexusIqSettings(null, null, null);

    public NexusIqSettings {
        serverUrl = trimToNull(serverUrl);
        credentialsId = trimToNull(credentialsId);
        scaScanName = trimToNull(scaScanName);
    }

    public void validate(ValidationProblems problems, List<NexusIqApplication> applications) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < applications.size(); i++) {
            NexusIqApplication application = applications.get(i);
            ValidationProblems at = problems.at("nexusIqApplications[" + i + "]");
            application.validate(at);
            if (application.application() != null && !seen.add(application.application())) {
                at.add("application", "is listed twice: each Nexus IQ application is scanned once");
            }
        }
    }

    public void writeTo(ConfigTree config, List<NexusIqApplication> applications) {
        String path = "tools.nexusIq.";
        config.set(path + "serverUrl", serverUrl).set(path + "credentialsId", credentialsId);
        if (applications.size() == 1) {
            NexusIqApplication only = applications.get(0);
            config.set(path + "application", only.application()).set(path + "scanPatterns", only.scanPatterns())
                    .set(path + "stage", only.stage()).set(path + "failOnNetworkError", only.failOnNetworkError());
        } else if (applications.size() > 1) {
            Map<String, Object> entries = new LinkedHashMap<>();
            applications.forEach(application -> entries.put(application.application(), new ConfigTree()
                    .set("scanPatterns", application.scanPatterns())
                    .set("serverUrl", config.get(path + "serverUrl"))
                    .set("credentialsId", config.get(path + "credentialsId"))
                    .set("stage", application.stage())
                    .set("failOnNetworkError", application.failOnNetworkError()).toMap()));
            config.set(path + "application", entries);
        }
        config.set("sca.scanName", scaScanName);
    }
}
