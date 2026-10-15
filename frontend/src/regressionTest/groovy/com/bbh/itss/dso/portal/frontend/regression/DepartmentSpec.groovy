package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.regression.ProductPageSpecification.DEPARTMENTS
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class DepartmentSpec extends DsoSpecification {

    static final String HAS_A_PRODUCT =
            'Only an empty department can be deleted. Fund Services still has 1 product: move it to another department first.'

    def "the departments are listed with their products, services and pipelines, and charted by their pipelines"() {
        when:
        open('/admin/departments')

        then:
        assertThat(names()).hasText(DEPARTMENTS as String[])
        assertThat(gridHeaders()).hasText(['Department', 'Products', 'Services', 'Pipelines', ''] as String[])
        assertThat(page.locator('.list-header h2')).hasText('Departments')
        assertThat(summary()).hasText('5 departments with 2 products and 6 services')
        assertThat(page.locator('section.departments .section-help')).containsText('Only an empty department can be deleted.')
        assertThat(cells('Fund Services')).hasText(['Fund Services', '1', '4', '6, 1 key invalidated'] as String[])
        assertThat(cells('Corporate Technology')).hasText(['Corporate Technology', '1', '2', '3, all active'] as String[])
        assertThat(cells('AI Lab')).hasText(['AI Lab', '0', '0', 'None yet'] as String[])
        assertThat(page.locator('section.chart h2')).hasText('Pipelines per department')
        assertThat(page.locator('section.chart .legend span')).hasText(['Active', 'Key invalidated'] as String[])
        assertThat(page.locator('section.chart .highcharts-xaxis-labels').nth(1).locator('text')).hasText(
                ['0 pipelines', '0 pipelines', '3 pipelines', '0 pipelines', '6 pipelines, 1 key invalidated'] as String[])
        assertThat(page.locator('section.chart dso-chart')).hasAttribute('aria-label',
                'AI Lab: none; Capital Partners: none; Corporate Technology: 3 active; Custody: none; ' +
                        'Fund Services: 5 active, 1 key invalidated')
        assertThat(page.locator('section.chart .highcharts-series.disabled .highcharts-point')).not().hasCount(0)
        api.requests('GET', '/api/departments').size() == 1
        ownErrors().isEmpty()
    }

    def "departments are added and renamed, and a name already in use is refused with the portal's message"() {
        given:
        open('/admin/departments')

        when:
        button('Add department', true).click()
        dialogButton('Add department').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Add a department')
        assertThat(errorOf(dialog(), 'Name')).hasText('Required')
        api.requests('POST', '/api/departments').isEmpty()

        when:
        input(dialog(), 'Name').fill('  Treasury ')
        dialogButton('Add department').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Treasury added')
        awaitRequest('POST', '/api/departments').json() == [name: 'Treasury']
        assertThat(names()).hasText((DEPARTMENTS + 'Treasury') as String[])
        assertThat(cells('Treasury')).hasText(['Treasury', '0', '0', 'None yet'] as String[])
        assertThat(summary()).hasText('6 departments with 2 products and 6 services')

        when:
        api.respond('PUT', '/api/departments/6', problem(409, 'Conflict', 'A department named Custody already exists'))
        buttonIn(row('Treasury'), 'Rename Treasury').click()
        input(dialog(), 'Name').fill('custody')
        dialogButton('Rename department').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Rename Treasury')
        assertThat(dialog().locator('[role=alert]')).hasText('A department named Custody already exists')
        awaitRequest('PUT', '/api/departments/6').json() == [name: 'custody', version: 0]

        when:
        dialogButton('Cancel').click()
        buttonIn(row('Capital Partners'), 'Rename Capital Partners').click()

        then:
        assertThat(input(dialog(), 'Name')).hasValue('Capital Partners')

        when:
        input(dialog(), 'Name').fill('Capital Partners Group')
        dialogButton('Rename department').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Capital Partners renamed to Capital Partners Group')
        awaitRequest('PUT', '/api/departments/2').json() == [name: 'Capital Partners Group', version: 0]
        assertThat(names()).hasText(['AI Lab', 'Capital Partners Group', 'Corporate Technology', 'Custody',
                                     'Fund Services', 'Treasury'] as String[])
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "only a department without products is deleted, once confirmed"() {
        given:
        open('/admin/departments')

        expect:
        assertThat(buttonIn(row('Fund Services'), 'Delete Fund Services')).isDisabled()
        assertThat(row('Fund Services').locator('.delete')).hasAttribute('title', HAS_A_PRODUCT)
        assertThat(buttonIn(row('Fund Services'), 'Delete Fund Services')).hasAccessibleDescription(HAS_A_PRODUCT)

        when:
        buttonIn(row('Custody'), 'Delete Custody').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Delete the department Custody?')
        assertThat(dialog()).containsText('This cannot be undone.')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('DELETE', '/api/departments/\\d+').isEmpty()

        when:
        buttonIn(row('Custody'), 'Delete Custody').click()
        dialogButton('Delete department').click()

        then:
        assertThat(snackBar()).containsText('Custody deleted')
        assertThat(names()).hasText((DEPARTMENTS - 'Custody') as String[])
        awaitRequest('DELETE', '/api/departments/4')

        when:
        api.respond('DELETE', '/api/departments/1', problem(409, 'Conflict',
                'AI Lab still has 1 product(s). Move them to another department first.'))
        buttonIn(row('AI Lab'), 'Delete AI Lab').click()
        dialogButton('Delete department').click()

        then:
        assertThat(snackBar()).containsText('AI Lab still has 1 product(s). Move them to another department first.')
        assertThat(names()).hasCount(4)
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "a department renamed here is shown under its new name in the product list"() {
        given:
        open('/admin/departments')

        when:
        buttonIn(row('Fund Services'), 'Rename Fund Services').click()
        input(dialog(), 'Name').fill('Fund Administration')
        dialogButton('Rename department').click()

        then:
        assertThat(row('Fund Administration')).hasCount(1)

        when:
        tab('Products').click()
        page.waitForURL('**/admin/products')

        then:
        assertThat(page.locator('section.department h2')).hasText(['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody',
                                                                   'Fund Administration'] as String[])
        ownErrors().isEmpty()
    }

    Locator summary() {
        page.locator('.list-header .summary')
    }

    Locator names() {
        gridRows().locator('.name')
    }

    Locator row(String name) {
        holding(gridRows(), ".name:text-is('${name}')")
    }

    Locator cells(String name) {
        row(name).locator(".ag-cell:not([col-id='actions'])")
    }
}
