package com.bbh.itss.dso.portal.adapter

import com.bbh.itss.dso.portal.adapter.in.web.ServiceDto
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.ToolCommand
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.support.Fixtures.settings

class RecordMapperSpec extends Specification {

    def "a record is copied onto another record component by component, nested records included"() {
        when:
        def dto = RecordMapper.map(settings().build(), ServiceDto.BuildSettingsDto)

        then:
        dto.tool() == settings().build().tool()
        dto.command() == ServiceDto.ToolCommandDto.builder().tasks(['clean', 'build']).flags([]).environment([])
                .returnStdout(false).build()
        RecordMapper.map(dto, settings().build().class) == settings().build()
    }

    def "a record that mirrors another one is normalised by it"() {
        expect:
        RecordMapper.map(ServiceDto.ToolCommandDto.builder().tasks([' clean ', ' ']).directory(' ').label(' ').build(),
                ServiceDto.ToolCommandDto) == ServiceDto.ToolCommandDto.builder().tasks(['clean']).flags([])
                .environment([]).returnStdout(false).build()
        RecordMapper.map(null, ToolCommand) == null
    }

    def "lists keep their empty entries and maps keyed by an enum follow its order"() {
        given:
        def targets = new LinkedHashMap()
        targets.put(QC, ServiceDto.SshTargetDto.builder().host('qc').build())
        targets.put(RD, null)
        def application = ServiceDto.UrbanCodeApplicationSettingsDto.builder().applicationName('Cert')
                .components([null]).build()

        expect:
        RecordMapper.map(application, ServiceDto.UrbanCodeApplicationSettingsDto).components() == [null]
        RecordMapper.map(service(targets), ServiceDto).sshTargets().keySet() as List == [RD, QC]
    }

    def "each component is read from the first source that has it"() {
        given:
        def service = new Service(3L, 'gui', null, 0, settings())

        when:
        def dto = RecordMapper.map(ServiceDto, service, service.settings())

        then:
        dto.id() == 3L
        dto.name() == 'gui'
        dto.appScan().applicationId() == service.settings().appScan().applicationId()
    }

    def "a record that refuses its components fails the mapping"() {
        when:
        RecordMapper.map(service([:]), ServiceSettings)

        then:
        def failure = thrown(NullPointerException)
        failure.message == 'a service needs its build settings'
    }

    private static ServiceDto service(Map targets) {
        ServiceDto.builder().name('gui').sshTargets(targets).build()
    }
}
