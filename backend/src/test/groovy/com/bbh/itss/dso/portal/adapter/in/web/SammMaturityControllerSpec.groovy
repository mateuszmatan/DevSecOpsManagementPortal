package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.samm.port.in.GetSammMaturityUseCase
import com.bbh.itss.dso.portal.domain.samm.SammApplicationScore
import com.bbh.itss.dso.portal.domain.samm.SammDepartmentScore
import com.bbh.itss.dso.portal.domain.samm.SammOrganizationOverview
import org.springframework.http.HttpStatus
import spock.lang.Specification

import java.time.Instant

class SammMaturityControllerSpec extends Specification {

    GetSammMaturityUseCase samm = Mock()
    SammMaturityController controller = new SammMaturityController(samm)

    def "overview returns organization overview"() {
        given:
        def now = Instant.parse('2026-11-04T12:00:00Z')
        def overview = new SammOrganizationOverview(5, 2, 2.4, 2, 'Defined', [], [], [], [:], now)
        samm.overview() >> overview

        when:
        def response = controller.overview()

        then:
        response.totalApplications() == 5
        response.overallScore() == 2.4
    }

    def "departments returns all department scores"() {
        given:
        def d1 = new SammDepartmentScore(1L, 'Advisory', 3, 2.5, 3, 'Optimized', [], [:])
        samm.departmentScores() >> [d1]

        when:
        def response = controller.departments()

        then:
        response.size() == 1
        response[0].departmentName() == 'Advisory'
    }

    def "department returns 200 when found or 404 when missing"() {
        given:
        def d1 = new SammDepartmentScore(1L, 'Advisory', 3, 2.5, 3, 'Optimized', [], [:])
        samm.departmentScore(1L) >> Optional.of(d1)
        samm.departmentScore(99L) >> Optional.empty()

        when:
        def ok = controller.department(1L)
        def notFound = controller.department(99L)

        then:
        ok.statusCode == HttpStatus.OK
        ok.body.departmentName() == 'Advisory'
        notFound.statusCode == HttpStatus.NOT_FOUND
    }

    def "application returns 200 when found or 404 when missing"() {
        given:
        def now = Instant.parse('2026-11-04T12:00:00Z')
        def app = new SammApplicationScore(100L, 'K', 'J', 1L, 'P', 2L, 'S', 3L, 'D', 2.1, 2, 'Defined', [], [], now)
        samm.applicationScore(100L) >> Optional.of(app)
        samm.applicationScore(999L) >> Optional.empty()

        when:
        def ok = controller.application(100L)
        def notFound = controller.application(999L)

        then:
        ok.statusCode == HttpStatus.OK
        ok.body.pipelineId() == 100L
        notFound.statusCode == HttpStatus.NOT_FOUND
    }
}
