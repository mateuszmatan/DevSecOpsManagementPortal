package com.bbh.itss.dso.portal.domain.bitbucket;

public record PrFinding(
        String tool,
        String severity,
        String file,
        Integer line,
        String title,
        String description,
        String remediation
) {
}
