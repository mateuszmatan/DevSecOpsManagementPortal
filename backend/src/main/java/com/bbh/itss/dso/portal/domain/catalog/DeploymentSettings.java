package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record DeploymentSettings(DeployTarget target, String appName, String artifactName,
                                 String baseArtifactName, Boolean nexusDeliveryDisabled) {

    public DeploymentSettings {
        appName = trimToNull(appName);
        artifactName = trimToNull(artifactName);
        baseArtifactName = trimToNull(baseArtifactName);
        nexusDeliveryDisabled = isTrue(nexusDeliveryDisabled);
    }

    /** Whether the snapshot stage delivers this service to Nexus from the given deploy target. */
    public boolean deliversToNexus(DeployTarget from) {
        return target == from && !nexusDeliveryDisabled;
    }

    public void writeTo(ConfigTree config) {
        config.set("deployTarget", target)
                .set("appName", appName)
                .set("artifactName", artifactName)
                .set("baseArtifactName", baseArtifactName);
    }

    public void validate(ValidationProblems problems) {
        if (target == OPENSHIFT) {
            problems.require("appName", appName, "is required for OpenShift deployment");
        }
        if (deliversToNexus(OPENSHIFT)) {
            problems.require("artifactName", artifactName, "is required for OpenShift deployment");
        }
    }
}
