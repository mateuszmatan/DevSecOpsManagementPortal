package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord
import com.bbh.itss.dso.portal.domain.user.UserProfile
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION

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

    def "a failure from #source is recorded with the reason the API gives, not the portal's internals"() {
        when:
        recorder.record(CREATE, { throw failure }, 'Created')

        then:
        thrown(RuntimeException)
        1 * actionLog.record(CREATE.withUser(user).failed(reason))

        where:
        source                     | failure                                                                        || reason
        'a concurrent change'      | new ObjectOptimisticLockingFailureException('ProductEntity', 12L)              || STALE_VERSION
        'a database constraint'    | new DataIntegrityViolationException('insert into DSO_DEPARTMENT violates UK') || 'The change violates a database constraint, most likely a duplicate name or key.'
        'an unexpected error'      | new NullPointerException('Cannot invoke "Product.id()"')                       || "The portal could not handle the request. The failure is in the portal's log."
        'an unknown product'       | new NoSuchElementException('Product 12 does not exist')                        || 'Product 12 does not exist'
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
