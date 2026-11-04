package com.bbh.itss.dso.portal.domain.samm;

import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings
import com.bbh.itss.dso.portal.domain.catalog.BuildSettings
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.Department
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.domain.catalog.Region
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.ToolCommand
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.domain.catalog.VersionCatalogRepo
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import com.bbh.itss.dso.portal.domain.report.PipelineReport
import com.bbh.itss.dso.portal.support.Fixtures
import spock.lang.Specification

import java.time.Instant

class SammCalculatorSpec extends Specification {

    def "level and tier mapping follows standard thresholds"() {
        expect:
        SammCalculator.levelFor(score) == level
        SammCalculator.tierFor(level) == tier

        where:
        score || level | tier
        0.0   || 0     | 'Initial'
        0.7   || 0     | 'Initial'
        0.8   || 1     | 'Foundational'
        1.5   || 1     | 'Foundational'
        1.6   || 2     | 'Defined'
        2.4   || 2     | 'Defined'
        2.5   || 3     | 'Optimized'
        3.0   || 3     | 'Optimized'
    }

    def "calculates mature SAMM scores when pipeline has full gates and successful report"() {
        given:
        def dept = new Department(1L, 'Core Banking', 1)
        def settings = Fixtures.settings(
                build: Fixtures.build(tool: BuildTool.MAVEN, autoSetup: true),
                unitTests: UnitTestSettings.builder().command(ToolCommand.of(['test'], [])).coverageReportPath('target/site/jacoco.xml').cypressEnabled(true).build(),
                appScan: AppScanSettings.builder().applicationId('app-1').keyId('key-1').insecureTls(true).dastEnabled(true).dastTargetUrl('https://app.bbh.com').build(),
                sonar: SonarSettings.builder().projectKey('bbh:app-1').build(),
                nexusIq: new NexusIqSettings('https://nexus.bbh.com', 'cred-1'),
                nexusIqApplications: [new NexusIqApplication('app-1', ['**/*.jar'], 'build', false)],
                goldenFix: GoldenFixPolicy.builder().enabled(true).build(),
                versionCatalogs: ['gradle/libs.versions.toml'],
                versionCatalogRepo: new VersionCatalogRepo('https://bitbucket.bbh.com/catalog.git', 'master', 'catalog', 'bbh-bot'),
                deployment: Fixtures.deployment(target: DeployTarget.OPENSHIFT, appName: 'app-1', nexusDeliveryDisabled: false),
                openShiftTargets: [(Region.QC): OpenShiftTarget.builder().projectBuild('qc-ns').healthCheckUrl('https://app.bbh.com/health').build()],
                metrics: MetricsSettings.of(true, 'app-1', 'qc')
        )
        def product = Fixtures.product(id: 10L, name: 'Billing', departmentId: 1L, services: [[id: 20L, name: 'billing-api', settings: settings]])
        def pipeline = Fixtures.pipeline(id: 100L, productId: 10L, serviceId: 20L)

        def report = new PipelineReport(
                1L, 100L, 'BILL-API', 'job-billing', 42L,
                RunResult.SUCCESS, 'main', 'abcdef', 120L,
                Instant.parse('2026-11-04T12:00:00Z'),
                10L, 10L, 0L, 0L, 0L, 0L, '<html>ok</html>'
        )
        def run = new PipelineRun(Instant.parse('2026-11-04T12:00:00Z'), RunResult.SUCCESS, 'main', 42L, 120L, 'abcdef', 'job-billing', 10L, 10L, 0L, 0L, 0L, 0L, 0L)

        when:
        def score = SammCalculator.evaluateApplication(pipeline, product, product.services()[0], dept, report, run, Instant.parse('2026-11-04T12:00:00Z'))

        then:
        score.pipelineId() == 100L
        score.departmentName() == 'Core Banking'
        score.maturityLevel() == 3
        score.maturityTier() == 'Optimized'
        score.domainScores().size() == 5
        score.practiceScores().size() == 11
        score.overallScore() >= 2.5
        score.domainScores().every { it.score() >= 2.0 }
        score.practiceScores().every { !it.criteriaMet().isEmpty() }
    }

    def "aggregates department score with level distribution"() {
        given:
        def dept = new Department(2L, 'Wealth Management', 1)
        def now = Instant.parse('2026-11-04T12:00:00Z')
        def app1 = new SammApplicationScore(1L, 'k1', 'j1', 10L, 'P1', 20L, 'S1', 2L, 'Wealth Management', 2.8, 3, 'Optimized', [], [], now)
        def app2 = new SammApplicationScore(2L, 'k2', 'j2', 10L, 'P1', 21L, 'S2', 2L, 'Wealth Management', 1.8, 2, 'Defined', [], [], now)

        when:
        def deptScore = SammCalculator.evaluateDepartment(dept, [app1, app2])

        then:
        deptScore.departmentId() == 2L
        deptScore.applicationCount() == 2
        deptScore.overallScore() == 2.3
        deptScore.maturityLevel() == 2
        deptScore.maturityTier() == 'Defined'
        deptScore.levelDistribution() == [0: 0, 1: 0, 2: 1, 3: 1]
    }

    def "aggregates organization overview across departments"() {
        given:
        def now = Instant.parse('2026-11-04T12:00:00Z')
        def dept1 = new Department(1L, 'Core Banking', 1)
        def dept2 = new Department(2L, 'Markets', 1)

        def app1 = new SammApplicationScore(1L, 'k1', 'j1', 10L, 'P1', 20L, 'S1', 1L, 'Core Banking', 2.8, 3, 'Optimized', [], [], now)
        def app2 = new SammApplicationScore(2L, 'k2', 'j2', 11L, 'P2', 22L, 'S2', 2L, 'Markets', 1.2, 1, 'Foundational', [], [], now)

        def dScore1 = SammCalculator.evaluateDepartment(dept1, [app1])
        def dScore2 = SammCalculator.evaluateDepartment(dept2, [app2])

        when:
        def org = SammCalculator.evaluateOrganization([dScore1, dScore2], [app1, app2], now)

        then:
        org.totalApplications() == 2
        org.totalDepartments() == 2
        org.overallScore() == 2.0
        org.maturityLevel() == 2
        org.maturityTier() == 'Defined'
        org.levelDistribution() == [0: 0, 1: 1, 2: 0, 3: 1]
    }
}
