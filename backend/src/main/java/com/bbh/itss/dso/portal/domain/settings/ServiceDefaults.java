package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

import static com.bbh.itss.dso.portal.domain.catalog.BuildSettings.DEFAULT_SOURCE_DIR;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ServiceDefaults(BuildTool buildTool, DeployTarget deployTarget, String sourceDir, Integer testsMaxParallel,
                              String nodeInstallationName, String npmRegistry, String npmrcCredentialsId) {

    public ServiceDefaults {
        sourceDir = defaultIfBlank(trim(sourceDir), DEFAULT_SOURCE_DIR);
        nodeInstallationName = trimToNull(nodeInstallationName);
        npmRegistry = trimToNull(npmRegistry);
        npmrcCredentialsId = trimToNull(npmrcCredentialsId);
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("buildTool", buildTool)
                .set("deployTarget", deployTarget)
                .set("sourceDir", sourceDir)
                .set("tests.maxParallel", testsMaxParallel);
        writeNodeTo(defaults);
    }

    /** The Node.js build values a service leaves to the Library defaults, as the library reads them. */
    public void writeNodeTo(ConfigTree config) {
        config.set("build.node.installationName", nodeInstallationName)
                .set("build.node.registry", npmRegistry)
                .set("build.node.npmrcCredentialsId", npmrcCredentialsId);
    }
}
