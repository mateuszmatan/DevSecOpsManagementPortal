package com.bbh.itss.dso.portal.domain.pipeline;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PipelineType {
    FULL("devSecOpsPipeline", "full", ""),
    SECURITY("devSecOpsSecurityPipeline", "security", "security"),
    EXTENDED("devSecOpsExtendedPipeline", "extended", "extended"),
    SAST("devSecOpsSASTScanningPipeline", "sast", "sast"),
    NEXUS_IQ("devSecOpsNexusIqGoldenFixPipeline", "nexusiq", "nexusiq");

    @Getter
    private final String entryPoint;
    @Getter
    private final String variant;
    private final String projectTagSuffix;

    public String influxProjectTag(String influxProject) {
        return influxProject + projectTagSuffix;
    }
}
