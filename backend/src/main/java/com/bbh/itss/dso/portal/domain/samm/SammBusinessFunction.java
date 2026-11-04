package com.bbh.itss.dso.portal.domain.samm;

public enum SammBusinessFunction {
    GOVERNANCE("Governance", "Strategy, policy, compliance and overall security posture alignment."),
    DESIGN("Design", "Threat-aware architecture, security requirements and dependency management."),
    IMPLEMENTATION("Implementation", "Secure build processes, defect tracking and deployment automation."),
    VERIFICATION("Verification", "Static code analysis, dependency scanning, code coverage and automated testing."),
    OPERATIONS("Operations", "Environment security, operational telemetry and incident readiness.");

    private final String title;
    private final String description;

    SammBusinessFunction(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
