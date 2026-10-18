package com.bbh.itss.dso.portal.application.catalog

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentUsagePort
import com.bbh.itss.dso.portal.domain.catalog.CatalogUsage
import com.bbh.itss.dso.portal.domain.catalog.Department
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.CatalogUsage.UNUSED
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static org.spockframework.mock.EmptyOrDummyResponse.INSTANCE

class DepartmentServiceSpec extends Specification {

    static final Department AI_LAB = new Department(1L, 'AI Lab', 0)
    static final Department CORPORATE = new Department(3L, 'corporate Technology', 1)
    static final Department FUND_SERVICES = new Department(5L, 'Fund Services', 0)
    static final CatalogUsage TWO_PRODUCTS = new CatalogUsage(2, 3, 4, 1)

    DepartmentRepositoryPort departments = Mock(defaultResponse: INSTANCE)
    DepartmentUsagePort usage = Stub() {
        perDepartment() >> [3L: TWO_PRODUCTS]
    }
    def service = new DepartmentService(departments, usage)

    def "departments are listed by name in any case with what the application counts for them, and one is found by its id"() {
        given:
        departments.findAll() >> [FUND_SERVICES, AI_LAB, CORPORATE, new Department(2L, 'Capital Partners', 0)]

        expect:
        service.list() == [new DepartmentView(1, 'AI Lab', 0, UNUSED),
                           new DepartmentView(2, 'Capital Partners', 0, UNUSED),
                           new DepartmentView(3, 'corporate Technology', 1, TWO_PRODUCTS),
                           new DepartmentView(5, 'Fund Services', 0, UNUSED)]
        service.get(3L) == new DepartmentView(3, 'corporate Technology', 1, TWO_PRODUCTS)
    }

    def "a new department is stored under its trimmed name and shown unused"() {
        when:
        def created = service.create(' Treasury ')

        then:
        1 * departments.save(new Department(null, 'Treasury', 0)) >> new Department(6L, 'Treasury', 0)
        departments.findAll() >> [AI_LAB, new Department(6L, 'Treasury', 0)]
        created == new DepartmentView(6, 'Treasury', 0, UNUSED)
    }

    def "a renamed department is shown with its usage"() {
        given:
        def stored = new Department(3L, 'Corporate Technology', 2)
        departments.load(3L) >> Optional.of(CORPORATE)

        when:
        def renamed = service.rename(3L, 1L, 'Corporate Technology')

        then:
        1 * departments.save(new Department(3L, 'Corporate Technology', 1)) >> stored
        departments.findAll() >> [stored]
        renamed == new DepartmentView(3, 'Corporate Technology', 2, TWO_PRODUCTS)
    }

    def "an unused department is deleted"() {
        given:
        departments.findAll() >> [AI_LAB, CORPORATE]

        when:
        service.delete(1L)

        then:
        1 * departments.delete(1L)
    }

    def "#refusal is refused before anything is stored"() {
        given:
        departments.findAll() >> [AI_LAB, CORPORATE, FUND_SERVICES]
        departments.load(3L) >> Optional.of(CORPORATE)
        departments.findByName('fund services') >> Optional.of(FUND_SERVICES)

        when:
        action(service)

        then:
        def e = thrown(IllegalStateException)
        e.message == message
        0 * departments.save(_)
        0 * departments.delete(_)

        where:
        refusal                                     | action                                        || message
        'a new name in use'                         | { it.create('fund services') }                || 'A business service named Fund Services already exists'
        'a rename to a name in use'                 | { it.rename(3L, 1L, 'fund services') }        || 'A business service named Fund Services already exists'
        'a rename of an old version'                | { it.rename(3L, 0L, 'Corporate Technology') } || STALE_VERSION
        'deleting a department with products'       | { it.delete(3L) }                             || 'corporate Technology still has 2 product(s). Move them to another business service first.'
    }

    def "an unknown department is not found, renamed or deleted"() {
        when:
        action(service)

        then:
        def e = thrown(NoSuchElementException)
        e.message == 'Business service 9 does not exist'
        0 * departments.save(_)
        0 * departments.delete(_)

        where:
        action << [{ it.get(9L) }, { it.rename(9L, 0L, 'Custody') }, { it.delete(9L) }]
    }
}
