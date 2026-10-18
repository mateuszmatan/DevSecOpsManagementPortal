package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord
import com.bbh.itss.dso.portal.domain.user.UserProfile
import spock.lang.Specification

class ActionRecorderSpec extends Specification {

    static final ActionRecord CREATE = ActionRecord.of('CREATE_DEPARTMENT', 'DEPARTMENT', 'Treasury', null)

    ActionLogUseCase actionLog = Mock()
    UserProfile user = UserProfile.defaultAdmin()
    def recorder = new ActionRecorder(actionLog, Stub(CurrentUserProvider) { getCurrentUser() >> user })

    def "a change that works is recorded by its user as done and its result returned"() {
        when:
        def result = recorder.record(CREATE, { 6L }, { action, id -> action.withDepartmentId(id).done('Created') })

        then:
        result == 6L
        1 * actionLog.record(CREATE.withUser(user).withDepartmentId(6L).done('Created'))
    }

    def "a change that fails is recorded as failed with the reason and the failure passed on"() {
        when:
        recorder.record(CREATE, { throw new IllegalStateException('Treasury already exists') }, 'Created')

        then:
        def failure = thrown(IllegalStateException)
        failure.message == 'Treasury already exists'
        1 * actionLog.record(CREATE.withUser(user).failed('Treasury already exists'))
    }

    def "the change stands when the history cannot record it"() {
        given:
        def done = false

        when:
        recorder.record(CREATE, { done = true } as Runnable, 'Created')

        then:
        1 * actionLog.record(_) >> { throw new IllegalStateException('database down') }
        done
        noExceptionThrown()
    }
}
