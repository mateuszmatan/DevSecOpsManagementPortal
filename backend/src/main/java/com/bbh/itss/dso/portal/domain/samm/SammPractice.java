package com.bbh.itss.dso.portal.domain.samm;

import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.DESIGN;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.GOVERNANCE;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.IMPLEMENTATION;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.OPERATIONS;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.VERIFICATION;

public enum SammPractice {
    STRATEGY_AND_METRICS(GOVERNANCE, "Strategy & Metrics", "SM", "Establish security goals, pipeline metrics telemetry and DORA indicators."),
    POLICY_AND_COMPLIANCE(GOVERNANCE, "Policy & Compliance", "PC", "Enforce organizational security baseline, toolchain gates and compliance verification."),
    SECURITY_REQUIREMENTS(DESIGN, "Security Requirements", "SR", "Govern software dependencies via Version Catalogs and secure component baselines."),
    SECURITY_ARCHITECTURE(DESIGN, "Security Architecture", "SA", "Architect containerized workloads, secret segregation and environment isolation."),
    SECURE_BUILD(IMPLEMENTATION, "Secure Build", "SB", "Automate isolated builds, dependency locking and reproducible toolchain provisioning."),
    SECURE_DEPLOYMENT(IMPLEMENTATION, "Secure Deployment", "SD", "Enforce binary artifact verification via Nexus and automated post-deployment validation."),
    DEFECT_MANAGEMENT(IMPLEMENTATION, "Defect Management", "DM", "Track vulnerabilities, filter noise and automate remediation via GoldenFix."),
    SECURITY_TESTING(VERIFICATION, "Security Testing", "ST", "Perform automated static application security testing (SAST) and component analysis (SCA)."),
    REQUIREMENTS_DRIVEN_TESTING(VERIFICATION, "Requirements-driven Testing", "RT", "Enforce unit testing suites, Cypress component tests and minimum 60% code coverage."),
    ENVIRONMENT_MANAGEMENT(OPERATIONS, "Environment Management", "EM", "Maintain infrastructure definitions, target namespaces and deployment health checks."),
    OPERATIONAL_MONITORING(OPERATIONS, "Operational Monitoring", "OM", "Stream fine-grained pipeline event telemetry and monitor operational performance.");

    private final SammBusinessFunction function;
    private final String title;
    private final String code;
    private final String description;

    SammPractice(SammBusinessFunction function, String title, String code, String description) {
        this.function = function;
        this.title = title;
        this.code = code;
        this.description = description;
    }

    public SammBusinessFunction getFunction() {
        return function;
    }

    public String getTitle() {
        return title;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
