package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record AppScanAccount(String keyId, String secretCredentialsId) {

    public AppScanAccount {
        keyId = trimToNull(keyId);
        secretCredentialsId = trimToNull(secretCredentialsId);
    }

    public void writeTo(ConfigTree config) {
        config.set("asoc.keyId", keyId).setIfAbsent("asoc.token", secretCredentialsId);
    }
}
