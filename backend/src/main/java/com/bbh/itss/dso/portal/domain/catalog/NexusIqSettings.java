package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record NexusIqSettings(String serverUrl, String credentialsId) {

    public static final NexusIqSettings NONE = new NexusIqSettings(null, null);
    public static final String ONE_APPLICATION = "a service scans one Nexus IQ application";

    public NexusIqSettings {
        serverUrl = trimToNull(serverUrl);
        credentialsId = trimToNull(credentialsId);
    }

    public void validate(ValidationProblems problems, List<NexusIqApplication> applications) {
        if (applications.size() > 1) {
            problems.add("nexusIqApplications", ONE_APPLICATION);
        }
        for (int i = 0; i < applications.size(); i++) {
            applications.get(i).validate(problems.at("nexusIqApplications[" + i + "]"));
        }
    }

    /** Services stored before the one-application rule keep their map of applications until an admin removes one. */
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
    }
}
