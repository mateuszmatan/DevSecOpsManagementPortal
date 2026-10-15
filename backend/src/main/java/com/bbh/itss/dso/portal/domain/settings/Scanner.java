package com.bbh.itss.dso.portal.domain.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Scanner {
    SAST("sast", "sast"),
    SCA("sca", "sca"),
    NEXUS_IQ("niq", "tools.nexusIq"),
    DAST("dast", "dast");

    private final String gateKey;
    private final String defaultsPath;
}
