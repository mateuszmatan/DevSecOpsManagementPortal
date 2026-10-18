package com.bbh.itss.dso.portal.application.actionlog

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord
import com.bbh.itss.dso.portal.application.actionlog.port.out.ActionLogRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.domain.actionlog.ActionLog
import com.bbh.itss.dso.portal.domain.catalog.Department
import com.bbh.itss.dso.portal.domain.user.UserProfile
import spock.lang.Specification

class ActionLogServiceSpec extends Specification {

    static final UserProfile ANN = new UserProfile(7L, 'ann', 'Ann Lee', 'ann@bbh.com', [], [], false)

    ActionLogRepositoryPort repository = Mock()
    DepartmentRepositoryPort departments = Stub() {
        load(4L) >> Optional.of(new Department(4L, 'Custody', 0))
        load(_) >> Optional.empty()
    }
    def service = new ActionLogService(repository, departments)

    def "a change is stored with its user, its trimmed name, the name of its department and its outcome"() {
        when:
        service.record(ActionRecord.of('CREATE_PRODUCT', 'PRODUCT', ' Payments ', departmentId).withUser(ANN)
                .done('Created product PAY with 2 service(s)'))
        service.record(ActionRecord.of('DELETE_PIPELINE', 'PIPELINE', 'api (FULL)', departmentId).withUser(ANN)
                .failed('Pipeline 9 does not exist'))

        then:
        1 * repository.save(new ActionLog(null, null, 'ann', 'Ann Lee', 'ann@bbh.com', department, 'CREATE_PRODUCT',
                'PRODUCT', 'Payments', 'Created product PAY with 2 service(s)', 'SUCCESS'))
        1 * repository.save(new ActionLog(null, null, 'ann', 'Ann Lee', 'ann@bbh.com', department, 'DELETE_PIPELINE',
                'PIPELINE', 'api (FULL)', 'Pipeline 9 does not exist', 'FAILURE'))

        where:
        departmentId | department
        4L           | 'Custody'
        99L          | null
        null         | null
    }

    def "a change too long for its columns is shortened"() {
        when:
        service.record(ActionRecord.of('UPDATE_PRODUCT', 'PRODUCT', 'n' * 300, null).withUser(ANN).done('d' * 5000))

        then:
        1 * repository.save({ ActionLog log -> log.resourceName().length() == 200 && log.details().length() == 4000 })
    }

    def "the history is listed newest first as stored"() {
        given:
        def stored = [new ActionLog(2L, null, 'ann', 'Ann Lee', null, null, 'DELETE_PRODUCT', 'PRODUCT', 'Pay', null,
                'SUCCESS')]
        repository.findAll() >> stored

        expect:
        service.list() == stored
    }
}
