package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import lombok.Builder;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record SshTarget(String host, String user, String deployDir, String deployScript, String versionFile) {

    public SshTarget {
        host = trimToNull(host);
        user = trimToNull(user);
        deployDir = trimToNull(deployDir);
        deployScript = trimToNull(deployScript);
        versionFile = trimToNull(versionFile);
    }

    public boolean isEmpty() {
        return toConfig().isEmpty();
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree()
                .set("host", host)
                .set("user", user)
                .set("deployDir", deployDir)
                .set("deployScript", deployScript)
                .set("versionFile", versionFile)
                .toMap();
    }
}
