package com.bbh.itss.dso.portal.domain.pipeline;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PipelineType {
    FULL("Full", "devSecOpsPipeline", "full", ""),
    SECURITY("Security", "devSecOpsSecurityPipeline", "security", "security"),
    EXTENDED("Extended", "devSecOpsExtendedPipeline", "extended", "extended"),
    SAST("SAST scanning", "devSecOpsSASTScanningPipeline", "sast", "sast"),
    NEXUS_IQ("Nexus IQ GoldenFix", "devSecOpsNexusIqGoldenFixPipeline", "nexusiq", "nexusiq");

    @Getter
    private final String label;
    @Getter
    private final String entryPoint;
    @Getter
    private final String variant;
    private final String projectTagSuffix;

    public String influxProjectTag(String influxProject) {
        return influxProject + projectTagSuffix;
    }
}
