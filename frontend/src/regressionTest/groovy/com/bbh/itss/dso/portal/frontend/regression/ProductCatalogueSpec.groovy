package com.bbh.itss.dso.portal.frontend.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
import static com.microsoft.playwright.options.AriaRole.LINK

class ProductCatalogueSpec extends ProductPageSpecification {

    def "the catalogue lists the products by department with the tally of each department's pipelines"() {
        when:
        open('/admin/products')

        then:
        assertThat(departmentNames()).hasText(DEPARTMENTS as String[])
        assertThat(department('Corporate Technology').locator('.tally')).hasText('1 product, 3 pipelines')
        assertThat(department('Fund Services').locator('.tally')).hasText('1 product, 6 pipelines, 1 key invalidated')
        assertThat(department('AI Lab').locator('.tally')).hasCount(0)
        assertThat(department('AI Lab').locator('.no-products')).hasText('No products in AI Lab yet.Add a product to AI Lab')
        assertThat(gridRows(department('Corporate Technology')).locator('a.name')).hasText('CertScanner')
        assertThat(gridRows(department('Fund Services')).locator('a.name')).hasText('Payments Hub')
        assertThat(gridHeaders(department('Fund Services')))
                .hasText(['Product', 'Owner team', 'Services', 'Pipelines'] as String[])
        assertThat(names()).hasText(['CertScanner', 'Payments Hub'] as String[])
        assertThat(page.locator('.toolbar .summary')).hasText('2 products in 5 business services')
        assertThat(page.locator('.toolbar .intro')).containsText('A pipeline is active while its key is valid')
        assertThat(page.getByRole(LINK, new Page.GetByRoleOptions().setName('Add product').setExact(true))).hasCount(1)
        assertThat(gridCell(row('Payments Hub'), 'services')).hasText('4')
        assertThat(row('Payments Hub').locator('.pipelines.revoked')).hasText('6, 1 key invalidated')
        assertThat(row('CertScanner').locator('.pipelines')).hasText('3, all active')
        assertThat(row('CertScanner').locator('.pipelines.revoked')).hasCount(0)
        api.requests('GET', '/api/products')*.params() == [[:]]
        api.requests('GET', '/api/departments').size() == 1
        ownErrors().isEmpty()
    }

    def "searching asks the API for the trimmed term and says when nothing matches"() {
        given:
        open('/admin/products')

        when:
        search().fill('  Payments  ')

        then:
        assertThat(names()).hasText(['Payments Hub'] as String[])
        assertThat(departmentNames()).hasText(['Fund Services'] as String[])
        assertThat(department('Fund Services').locator('.tally')).hasText('1 product, 6 pipelines, 1 key invalidated')
        assertThat(page.locator('.toolbar .summary')).hasText('1 product in 1 business service')
        api.lastRequest('GET', '/api/products').params() == [search: 'Payments']

        when:
        search().fill('corporate')

        then:
        assertThat(names()).hasText(['CertScanner'] as String[])
        assertThat(departmentNames()).hasText(['Corporate Technology'] as String[])

        when:
        search().fill('ledger-x')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No product matches "ledger-x"')
        assertThat(page.locator('.empty-state')).containsText('Try another name, code, team or business service.')
        assertThat(page.locator('.toolbar .summary')).hasText('0 products in 0 business services')

        when:
        button('Clear the search', true).click()

        then:
        assertThat(search()).hasValue('')
        assertThat(names()).hasText(['CertScanner', 'Payments Hub'] as String[])
        assertThat(departmentNames()).hasText(DEPARTMENTS as String[])
        api.lastRequest('GET', '/api/products').params() == [:]
        ownErrors().isEmpty()
    }

    Locator names() {
        gridRows().locator('a.name')
    }

    Locator row(String name) {
        holding(gridRows(), "a.name:text-is('${name}')")
    }

    Locator search() {
        page.getByLabel('Search products')
    }
}
