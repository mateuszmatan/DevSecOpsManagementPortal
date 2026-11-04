package com.bbh.itss.dso.portal.domain.samm;

import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import com.bbh.itss.dso.portal.domain.report.PipelineReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.DESIGN;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.GOVERNANCE;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.IMPLEMENTATION;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.OPERATIONS;
import static com.bbh.itss.dso.portal.domain.samm.SammBusinessFunction.VERIFICATION;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.DEFECT_MANAGEMENT;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.ENVIRONMENT_MANAGEMENT;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.OPERATIONAL_MONITORING;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.POLICY_AND_COMPLIANCE;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.REQUIREMENTS_DRIVEN_TESTING;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.SECURE_BUILD;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.SECURE_DEPLOYMENT;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.SECURITY_ARCHITECTURE;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.SECURITY_REQUIREMENTS;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.SECURITY_TESTING;
import static com.bbh.itss.dso.portal.domain.samm.SammPractice.STRATEGY_AND_METRICS;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

public final class SammCalculator {

    private SammCalculator() {}

    public static int levelFor(double score) {
        if (score >= 2.5) {
            return 3;
        }
        if (score >= 1.6) {
            return 2;
        }
        if (score >= 0.8) {
            return 1;
        }
        return 0;
    }

    public static String tierFor(int level) {
        return switch (level) {
            case 3 -> "Optimized";
            case 2 -> "Defined";
            case 1 -> "Foundational";
            default -> "Initial";
        };
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public static SammApplicationScore evaluateApplication(Pipeline pipeline, Product product, Service service,
                                                           Department department, PipelineReport report,
                                                           PipelineRun run, Instant assessedAt) {
        ServiceSettings settings = service.settings();

        Long deptId = department != null ? department.id() : product.details().departmentId();
        String deptName = department != null ? department.name() : "Unassigned";

        List<SammPracticeScore> practiceScores = new ArrayList<>();
        practiceScores.add(evaluateStrategyAndMetrics(settings, report, run));
        practiceScores.add(evaluatePolicyAndCompliance(settings, report, run));
        practiceScores.add(evaluateSecurityRequirements(settings, report, run));
        practiceScores.add(evaluateSecurityArchitecture(settings, report, run));
        practiceScores.add(evaluateSecureBuild(settings, report, run));
        practiceScores.add(evaluateSecureDeployment(settings, report, run));
        practiceScores.add(evaluateDefectManagement(settings, report, run));
        practiceScores.add(evaluateSecurityTesting(settings, report, run));
        practiceScores.add(evaluateRequirementsTesting(settings, report, run));
        practiceScores.add(evaluateEnvironmentManagement(settings, report, run));
        practiceScores.add(evaluateOperationalMonitoring(settings, report, run));

        Map<SammBusinessFunction, List<SammPracticeScore>> byFunction = new EnumMap<>(SammBusinessFunction.class);
        for (SammBusinessFunction fn : SammBusinessFunction.values()) {
            byFunction.put(fn, new ArrayList<>());
        }
        for (SammPracticeScore ps : practiceScores) {
            byFunction.get(ps.businessFunction()).add(ps);
        }

        List<SammDomainScore> domainScores = new ArrayList<>();
        double domainSum = 0.0;
        for (SammBusinessFunction fn : SammBusinessFunction.values()) {
            List<SammPracticeScore> practices = byFunction.get(fn);
            double avg = practices.stream().mapToDouble(SammPracticeScore::score).average().orElse(0.0);
            double roundedAvg = round1(avg);
            domainSum += roundedAvg;
            domainScores.add(new SammDomainScore(
                    fn,
                    fn.getTitle(),
                    fn.getDescription(),
                    roundedAvg,
                    levelFor(roundedAvg),
                    practices
            ));
        }

        double overall = round1(domainSum / 5.0);
        int overallLevel = levelFor(overall);

        return new SammApplicationScore(
                pipeline.id(),
                pipeline.activeKey().map(PipelineKey::value).orElse(null),
                pipeline.settings().jenkinsJob(),
                product.id(),
                product.name(),
                service.id(),
                service.name(),
                deptId,
                deptName,
                overall,
                overallLevel,
                tierFor(overallLevel),
                domainScores,
                practiceScores,
                assessedAt != null ? assessedAt : Instant.now()
        );
    }

    private static SammPracticeScore evaluateStrategyAndMetrics(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        if (settings.metrics() != null && isTrue(settings.metrics().enabled())) {
            score += 1.0;
            criteria.add("Pipeline stage metrics telemetry is enabled for InfluxDB and Grafana tracking");
        } else {
            recommendations.add("Enable metrics telemetry in service settings to record stage performance indicators");
        }

        if (run != null || report != null) {
            score += 1.0;
            criteria.add("Continuous execution telemetry actively collected from pipeline runs");
        } else {
            recommendations.add("Execute automated pipeline to establish baseline metrics");
        }

        boolean successful = (report != null && report.result() == RunResult.SUCCESS)
                || (run != null && run.result() == RunResult.SUCCESS);
        if (successful) {
            score += 1.0;
            criteria.add("Pipeline achieves stable execution with zero breaking SLA violations");
        } else {
            recommendations.add("Stabilize pipeline executions to meet organizational security SLA goals");
        }

        int level = levelFor(score);
        return new SammPracticeScore(STRATEGY_AND_METRICS, STRATEGY_AND_METRICS.name(),
                STRATEGY_AND_METRICS.getTitle(), STRATEGY_AND_METRICS.getCode(),
                GOVERNANCE, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluatePolicyAndCompliance(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        boolean hasSast = settings.appScan() != null && settings.appScan().keyId() != null;
        boolean hasSca = settings.nexusIq() != null && !settings.nexusIqApplications().isEmpty();
        boolean hasSonar = settings.sonar() != null && settings.sonar().projectKey() != null;

        if (hasSast || hasSca || hasSonar) {
            score += 1.0;
            criteria.add("Automated security gate integrated into delivery workflow");
        } else {
            recommendations.add("Integrate automated security scanning into pipeline configuration");
        }

        if (hasSast && hasSca && hasSonar) {
            score += 1.0;
            criteria.add("Full 3-tier security baseline (AppScan SAST, Nexus IQ SCA, SonarQube) enforced");
        } else {
            recommendations.add("Enable all baseline security scanners: AppScan SAST, Nexus IQ SCA, and SonarQube");
        }

        boolean passingGates = (report != null && report.blocked() != null && report.blocked() == 0 && report.failed() != null && report.failed() == 0)
                || (settings.goldenFix() != null && settings.goldenFix().enabled() != Boolean.FALSE);
        if (passingGates) {
            score += 1.0;
            criteria.add("Compliance verification passing without blocking defects or with active GoldenFix remediation");
        } else {
            recommendations.add("Eliminate blocking security issues and activate GoldenFix remediation policy");
        }

        int level = levelFor(score);
        return new SammPracticeScore(POLICY_AND_COMPLIANCE, POLICY_AND_COMPLIANCE.name(),
                POLICY_AND_COMPLIANCE.getTitle(), POLICY_AND_COMPLIANCE.getCode(),
                GOVERNANCE, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateSecurityRequirements(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        if (settings.build() != null && settings.build().tool() != null) {
            score += 1.0;
            criteria.add("Standardized declarative build toolchain defined (" + settings.build().tool() + ")");
        } else {
            recommendations.add("Define a standard build toolchain in service configuration");
        }

        boolean hasCatalog = (settings.versionCatalogs() != null && !settings.versionCatalogs().isEmpty())
                || (settings.versionCatalogRepo() != null && settings.versionCatalogRepo().isPresent());
        if (hasCatalog) {
            score += 1.0;
            criteria.add("Centralized Version Catalog repository integrated for dependency governance");
        } else {
            recommendations.add("Adopt central Version Catalog repository to standardize and govern approved dependencies");
        }

        if (hasCatalog && settings.goldenFix() != null && isTrue(settings.goldenFix().enabled())) {
            score += 1.0;
            criteria.add("Automated dependency governance combined with active GoldenFix upgrade pull requests");
        } else {
            recommendations.add("Enable GoldenFix automated remediation to continuously align dependencies with catalog versions");
        }

        int level = levelFor(score);
        return new SammPracticeScore(SECURITY_REQUIREMENTS, SECURITY_REQUIREMENTS.name(),
                SECURITY_REQUIREMENTS.getTitle(), SECURITY_REQUIREMENTS.getCode(),
                DESIGN, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateSecurityArchitecture(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        if (settings.deployment() != null && settings.deployment().target() != null) {
            score += 1.0;
            criteria.add("Explicit target deployment architecture configured (" + settings.deployment().target() + ")");
        } else {
            recommendations.add("Specify deployment target architecture in service settings");
        }

        boolean isOpenShift = settings.deployment() != null && settings.deployment().target() == DeployTarget.OPENSHIFT;
        if (isOpenShift) {
            score += 1.0;
            criteria.add("Containerized deployment architecture on OpenShift with namespace segregation");
        } else {
            recommendations.add("Migrate deployment target to containerized OpenShift environment");
        }

        boolean multiTarget = settings.openShiftTargets() != null && !settings.openShiftTargets().isEmpty();
        if (multiTarget) {
            score += 1.0;
            criteria.add("Target environments segregated with dedicated credentials and configuration");
        } else {
            recommendations.add("Configure explicit target environments with segregated credentials");
        }

        int level = levelFor(score);
        return new SammPracticeScore(SECURITY_ARCHITECTURE, SECURITY_ARCHITECTURE.name(),
                SECURITY_ARCHITECTURE.getTitle(), SECURITY_ARCHITECTURE.getCode(),
                DESIGN, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateSecureBuild(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        if (settings.build() != null && isTrue(settings.build().autoSetup())) {
            score += 1.0;
            criteria.add("Automated toolchain auto-setup ensures reproducible and isolated build environment");
        } else {
            recommendations.add("Enable auto-setup for build toolchain to guarantee environment reproducibility");
        }

        score += 1.0;
        criteria.add("Build stage isolated from test and security execution; static analysis runs in parallel");

        boolean cleanBuild = report == null || (report.failed() != null && report.failed() == 0);
        if (cleanBuild) {
            score += 1.0;
            criteria.add("Zero build breakages in latest pipeline verification");
        } else {
            recommendations.add("Resolve build failures to re-establish build integrity");
        }

        int level = levelFor(score);
        return new SammPracticeScore(SECURE_BUILD, SECURE_BUILD.name(),
                SECURE_BUILD.getTitle(), SECURE_BUILD.getCode(),
                IMPLEMENTATION, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateSecureDeployment(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        if (settings.deployment() != null && settings.deployment().target() != null) {
            score += 1.0;
            criteria.add("Automated deployment pipeline configured with target environment packaging");
        } else {
            recommendations.add("Configure automated deployment stage");
        }

        boolean nexusDelivery = settings.deployment() != null && !isTrue(settings.deployment().nexusDeliveryDisabled());
        if (nexusDelivery) {
            score += 1.0;
            criteria.add("Binary release artifacts published and verified through Nexus repository");
        } else {
            recommendations.add("Enable Nexus delivery to stage and verify immutable release binaries");
        }

        boolean automatedTests = (settings.testJobs() != null && !settings.testJobs().isEmpty())
                || (settings.tests() != null && (isTrue(settings.tests().smokeRequired()) || isTrue(settings.tests().regressionRequired())));
        if (automatedTests) {
            score += 1.0;
            criteria.add("Automated post-deployment verification tests (Smoke/Regression) configured");
        } else {
            recommendations.add("Configure automated post-deployment smoke or regression test jobs");
        }

        int level = levelFor(score);
        return new SammPracticeScore(SECURE_DEPLOYMENT, SECURE_DEPLOYMENT.name(),
                SECURE_DEPLOYMENT.getTitle(), SECURE_DEPLOYMENT.getCode(),
                IMPLEMENTATION, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateDefectManagement(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        if (report != null) {
            score += 1.0;
            criteria.add("Centralized pipeline report ingestion tracks defect and vulnerability trends");
        } else {
            recommendations.add("Publish pipeline execution reports to DevSecOps portal for defect tracking");
        }

        boolean noiseFilter = settings.appScan() != null && settings.appScan().keyId() != null;
        if (noiseFilter) {
            score += 1.0;
            criteria.add("Automated vulnerability triage active with noise-filtered SAST findings");
        } else {
            recommendations.add("Configure noise filtering for static application security scanning");
        }

        if (settings.goldenFix() != null && isTrue(settings.goldenFix().enabled())) {
            score += 1.0;
            criteria.add("GoldenFix automated pull requests enabled for rapid vulnerability remediation");
        } else {
            recommendations.add("Enable GoldenFix automated remediation to streamline defect fix lifecycles");
        }

        int level = levelFor(score);
        return new SammPracticeScore(DEFECT_MANAGEMENT, DEFECT_MANAGEMENT.name(),
                DEFECT_MANAGEMENT.getTitle(), DEFECT_MANAGEMENT.getCode(),
                IMPLEMENTATION, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateSecurityTesting(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        AppScanSettings appScan = settings.appScan();
        boolean hasSast = appScan != null && appScan.keyId() != null;
        boolean hasSca = settings.nexusIq() != null && !settings.nexusIqApplications().isEmpty();

        if (hasSast || hasSca) {
            score += 1.0;
            criteria.add("Static application security scanning active in pipeline workflow");
        } else {
            recommendations.add("Configure AppScan SAST or Nexus IQ SCA static security scanning");
        }

        if (hasSast && hasSca) {
            score += 1.0;
            criteria.add("Dual-engine static testing: SAST (source code) and SCA (third-party dependencies) executed in parallel");
        } else {
            recommendations.add("Enable both AppScan SAST and Nexus IQ SCA to run in parallel");
        }

        boolean advanced = (appScan != null && isTrue(appScan.dastEnabled()))
                || (settings.sonar() != null && settings.sonar().projectKey() != null);
        if (advanced) {
            score += 1.0;
            criteria.add("Advanced security verification active: SonarQube quality gates and/or dynamic analysis (DAST)");
        } else {
            recommendations.add("Integrate SonarQube code quality gates or DAST dynamic application scanning");
        }

        int level = levelFor(score);
        return new SammPracticeScore(SECURITY_TESTING, SECURITY_TESTING.name(),
                SECURITY_TESTING.getTitle(), SECURITY_TESTING.getCode(),
                VERIFICATION, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateRequirementsTesting(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        UnitTestSettings ut = settings.unitTests();
        boolean hasUnitTests = ut != null && (!ut.command().isEmpty() || ut.cypressEnabled());

        if (hasUnitTests) {
            score += 1.0;
            criteria.add("Automated unit test execution configured in pipeline workflow");
        } else {
            recommendations.add("Configure automated unit testing in service settings");
        }

        boolean hasCoverageOrCypress = ut != null && (ut.coverageReportPath() != null || ut.cypressEnabled());
        if (hasCoverageOrCypress) {
            score += 1.0;
            criteria.add("Code coverage threshold tracking (minimum 60%) or Cypress component testing active");
        } else {
            recommendations.add("Configure code coverage report (JaCoCo/LCOV) or Cypress component tests");
        }

        boolean passedTests = report != null && (report.failed() == null || report.failed() == 0)
                && (report.passed() != null && report.passed() > 0);
        if (passedTests) {
            score += 1.0;
            criteria.add("All unit and component tests passed in latest verification run");
        } else {
            recommendations.add("Achieve 100% test pass rate in pipeline execution");
        }

        int level = levelFor(score);
        return new SammPracticeScore(REQUIREMENTS_DRIVEN_TESTING, REQUIREMENTS_DRIVEN_TESTING.name(),
                REQUIREMENTS_DRIVEN_TESTING.getTitle(), REQUIREMENTS_DRIVEN_TESTING.getCode(),
                VERIFICATION, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateEnvironmentManagement(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        if (settings.deployment() != null && settings.deployment().target() != null) {
            score += 1.0;
            criteria.add("Explicit deployment target architecture defined");
        } else {
            recommendations.add("Define runtime target architecture");
        }

        boolean targetsConfigured = (settings.openShiftTargets() != null && !settings.openShiftTargets().isEmpty())
                || (settings.sshTargets() != null && !settings.sshTargets().isEmpty());
        if (targetsConfigured) {
            score += 1.0;
            criteria.add("Managed infrastructure endpoints configured with credential isolation");
        } else {
            recommendations.add("Configure target environment connection endpoints and credentials");
        }

        boolean stable = (report != null && report.result() == RunResult.SUCCESS)
                || (run != null && run.result() == RunResult.SUCCESS);
        if (stable) {
            score += 1.0;
            criteria.add("Operational deployment verified stable in target environment");
        } else {
            recommendations.add("Achieve stable automated release execution into target environment");
        }

        int level = levelFor(score);
        return new SammPracticeScore(ENVIRONMENT_MANAGEMENT, ENVIRONMENT_MANAGEMENT.name(),
                ENVIRONMENT_MANAGEMENT.getTitle(), ENVIRONMENT_MANAGEMENT.getCode(),
                OPERATIONS, round1(score), level, criteria, recommendations);
    }

    private static SammPracticeScore evaluateOperationalMonitoring(ServiceSettings settings, PipelineReport report, PipelineRun run) {
        List<String> criteria = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        double score = 0.0;

        score += 1.0;
        criteria.add("Pipeline registered in centralized DevSecOps monitoring inventory");

        if (settings.metrics() != null && isTrue(settings.metrics().enabled())) {
            score += 1.0;
            criteria.add("Fine-grained stage timing, status, and defect metrics emitted to InfluxDB and Grafana");
        } else {
            recommendations.add("Enable metrics emission to feed centralized Grafana operational dashboards");
        }

        if (run != null) {
            score += 1.0;
            criteria.add("Active pipeline run telemetry established for DORA metric evaluation");
        } else {
            recommendations.add("Run pipeline to populate continuous delivery operational indicators");
        }

        int level = levelFor(score);
        return new SammPracticeScore(OPERATIONAL_MONITORING, OPERATIONAL_MONITORING.name(),
                OPERATIONAL_MONITORING.getTitle(), OPERATIONAL_MONITORING.getCode(),
                OPERATIONS, round1(score), level, criteria, recommendations);
    }

    public static SammDepartmentScore evaluateDepartment(Department department, List<SammApplicationScore> applicationScores) {
        int count = applicationScores.size();
        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        for (int l = 0; l <= 3; l++) {
            distribution.put(l, 0);
        }
        for (SammApplicationScore app : applicationScores) {
            distribution.merge(app.maturityLevel(), 1, Integer::sum);
        }

        if (count == 0) {
            List<SammDomainScore> emptyDomains = new ArrayList<>();
            for (SammBusinessFunction fn : SammBusinessFunction.values()) {
                emptyDomains.add(new SammDomainScore(fn, fn.getTitle(), fn.getDescription(), 0.0, 0, List.of()));
            }
            return new SammDepartmentScore(
                    department.id(),
                    department.name(),
                    0,
                    0.0,
                    0,
                    tierFor(0),
                    emptyDomains,
                    distribution
            );
        }

        double overallSum = applicationScores.stream().mapToDouble(SammApplicationScore::overallScore).sum();
        double overallAvg = round1(overallSum / count);
        int deptLevel = levelFor(overallAvg);

        List<SammDomainScore> domainScores = new ArrayList<>();
        for (SammBusinessFunction fn : SammBusinessFunction.values()) {
            double domainAvg = applicationScores.stream()
                    .mapToDouble(app -> app.domainScores().stream()
                            .filter(d -> d.function() == fn)
                            .mapToDouble(SammDomainScore::score)
                            .findFirst().orElse(0.0))
                    .average().orElse(0.0);
            double rounded = round1(domainAvg);
            domainScores.add(new SammDomainScore(fn, fn.getTitle(), fn.getDescription(), rounded, levelFor(rounded), List.of()));
        }

        return new SammDepartmentScore(
                department.id(),
                department.name(),
                count,
                overallAvg,
                deptLevel,
                tierFor(deptLevel),
                domainScores,
                distribution
        );
    }

    public static SammOrganizationOverview evaluateOrganization(List<SammDepartmentScore> departments,
                                                                 List<SammApplicationScore> applications,
                                                                 Instant generatedAt) {
        int totalApps = applications.size();
        int totalDepts = departments.size();

        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        for (int l = 0; l <= 3; l++) {
            distribution.put(l, 0);
        }
        for (SammApplicationScore app : applications) {
            distribution.merge(app.maturityLevel(), 1, Integer::sum);
        }

        if (totalApps == 0) {
            List<SammDomainScore> emptyDomains = new ArrayList<>();
            for (SammBusinessFunction fn : SammBusinessFunction.values()) {
                emptyDomains.add(new SammDomainScore(fn, fn.getTitle(), fn.getDescription(), 0.0, 0, List.of()));
            }
            return new SammOrganizationOverview(
                    0,
                    totalDepts,
                    0.0,
                    0,
                    tierFor(0),
                    emptyDomains,
                    departments,
                    applications,
                    distribution,
                    generatedAt != null ? generatedAt : Instant.now()
            );
        }

        double overallSum = applications.stream().mapToDouble(SammApplicationScore::overallScore).sum();
        double overallAvg = round1(overallSum / totalApps);
        int orgLevel = levelFor(overallAvg);

        List<SammDomainScore> domainScores = new ArrayList<>();
        for (SammBusinessFunction fn : SammBusinessFunction.values()) {
            double domainAvg = applications.stream()
                    .mapToDouble(app -> app.domainScores().stream()
                            .filter(d -> d.function() == fn)
                            .mapToDouble(SammDomainScore::score)
                            .findFirst().orElse(0.0))
                    .average().orElse(0.0);
            double rounded = round1(domainAvg);
            domainScores.add(new SammDomainScore(fn, fn.getTitle(), fn.getDescription(), rounded, levelFor(rounded), List.of()));
        }

        return new SammOrganizationOverview(
                totalApps,
                totalDepts,
                overallAvg,
                orgLevel,
                tierFor(orgLevel),
                domainScores,
                departments,
                applications,
                distribution,
                generatedAt != null ? generatedAt : Instant.now()
        );
    }
}
