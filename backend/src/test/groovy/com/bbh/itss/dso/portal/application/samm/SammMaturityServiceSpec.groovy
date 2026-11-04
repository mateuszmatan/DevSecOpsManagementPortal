package com.bbh.itss.dso.portal.application.samm;

import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.report.port.out.PipelineReportPort
import com.bbh.itss.dso.portal.domain.catalog.Department
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings
import com.bbh.itss.dso.portal.support.Fixtures
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SammMaturityServiceSpec extends Specification {

    ReadMonitoringTargetsUseCase monitoringTargets = Mock()
    DepartmentRepositoryPort departments = Mock()
    PipelineReportPort reports = Mock()
    PipelineRunsPort runs = Mock()
    Clock clock = Clock.fixed(Instant.parse('2026-11-04T12:00:00Z'), ZoneOffset.UTC)

    SammMaturityService service = new SammMaturityService(monitoringTargets, departments, reports, runs, clock)

    def "overview calculates SAMM scores across all applications and departments"() {
        given:
        def dept1 = new Department(1L, 'Corporate Advisory', 1)
        def dept2 = new Department(2L, 'Custody & Clearing', 1)

        def product = Fixtures.product(id: 10L, name: 'Advisor Portal', departmentId: 1L, services: [[id: 20L, name: 'advisor-api']])
        def pipeline = Fixtures.pipeline(id: 100L, productId: 10L, serviceId: 20L)
        def view = new PipelineView(product, product.services()[0], pipeline, 'https://jenkins.bbh.com')

        def platform = PlatformSettings.builder().jenkinsUrl('https://jenkins.bbh.com').build()
        def targets = new MonitoringTargets([product], [view], platform, [] as Set)

        departments.findAll() >> [dept1, dept2]
        monitoringTargets.everything() >> targets
        runs.configured(platform) >> false
        reports.findLatestByPipelineId(100L) >> Optional.empty()

        when:
        def overview = service.overview()

        then:
        overview.totalApplications() == 1
        overview.totalDepartments() == 2
        overview.applications().size() == 1
        overview.applications()[0].pipelineId() == 100L
        overview.applications()[0].departmentName() == 'Corporate Advisory'
        overview.departments().size() == 2
        overview.domainScores().size() == 5
    }

    def "queries department and application score by id"() {
        given:
        def dept = new Department(5L, 'Treasury', 1)
        def product = Fixtures.product(id: 10L, name: 'FX Desk', departmentId: 5L, services: [[id: 30L, name: 'fx-api']])
        def pipeline = Fixtures.pipeline(id: 200L, productId: 10L, serviceId: 30L)
        def view = new PipelineView(product, product.services()[0], pipeline, 'https://jenkins.bbh.com')
        def platform = PlatformSettings.builder().jenkinsUrl('https://jenkins.bbh.com').build()
        def targets = new MonitoringTargets([product], [view], platform, [] as Set)

        departments.findAll() >> [dept]
        monitoringTargets.everything() >> targets
        runs.configured(platform) >> false
        reports.findLatestByPipelineId(200L) >> Optional.empty()

        when:
        def app = service.applicationScore(200L)
        def d = service.departmentScore(5L)
        def missing = service.applicationScore(999L)

        then:
        app.isPresent()
        app.get().pipelineId() == 200L
        d.isPresent()
        d.get().departmentId() == 5L
        missing.isEmpty()
    }
}
