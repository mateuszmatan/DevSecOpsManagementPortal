package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import lombok.Builder;

import java.util.List;

import static org.apache.commons.lang3.BooleanUtils.isNotFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record UrbanCodeSettings(String siteName, String deployProcess, Boolean skipWait, Boolean deployWithSnapshot,
                                Boolean updateSnapshotComponents, Boolean includeOnlyDeployVersions,
                                Boolean deployOnlyChanged, String deployDescription, String requestProperties) {

    public static final UrbanCodeSettings DEFAULTS = builder().build();

    public UrbanCodeSettings {
        siteName = trimToNull(siteName);
        deployProcess = trimToNull(deployProcess);
        skipWait = isTrue(skipWait);
        deployWithSnapshot = isNotFalse(deployWithSnapshot);
        updateSnapshotComponents = isTrue(updateSnapshotComponents);
        includeOnlyDeployVersions = isNotFalse(includeOnlyDeployVersions);
        deployOnlyChanged = isTrue(deployOnlyChanged);
        deployDescription = trimToNull(deployDescription);
        requestProperties = trimToNull(requestProperties);
    }

    public void writeTo(ConfigTree config, List<UrbanCodeApplicationSettings> applications) {
        if (applications.isEmpty()) {
            return;
        }
        String path = "deploy.vm.dod.";
        config.set(path + "siteName", siteName)
                .set(path + "deployProcess", deployProcess)
                .set(path + "skipWait", skipWait)
                .set(path + "deployWithSnapshot", deployWithSnapshot)
                .set(path + "updateSnapshotComp", updateSnapshotComponents)
                .set(path + "includeOnlyDeployVersions", includeOnlyDeployVersions)
                .set(path + "deployOnlyChanged", deployOnlyChanged)
                .set(path + "deployDescription", deployDescription)
                .set(path + "requestProperties", requestProperties)
                .set(path + "applications", applications.stream().map(UrbanCodeApplicationSettings::toConfig).toList());
    }
}
