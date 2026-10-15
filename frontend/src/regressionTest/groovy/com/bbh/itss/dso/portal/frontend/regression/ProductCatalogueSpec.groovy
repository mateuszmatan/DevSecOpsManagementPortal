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
        assertThat(page.locator('.toolbar .summary')).hasText('2 products in 5 departments')
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

    def "products without a department are listed last until they are edited"() {
        given:
        def products = fixture('products.json') as List<Map>
        api.respond('GET', '/api/products', [products[0], products[1] + [departmentId: null, departmentName: null]])

        when:
        open('/admin/products')

        then:
        assertThat(departmentNames()).hasText((DEPARTMENTS + 'Not in a department') as String[])
        assertThat(gridRows(department('Not in a department')).locator('a.name')).hasText('Payments Hub')
        assertThat(department('Not in a department').locator('.tally')).hasText('1 product, 6 pipelines, 1 key invalidated')
        assertThat(department('Not in a department').locator('.hint')).hasText('Edit these products to choose their department.')
        assertThat(department('Not in a department').getByRole(BUTTON)).hasCount(0)
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
        assertThat(page.locator('.toolbar .summary')).hasText('1 product in 1 department')
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
        assertThat(page.locator('.empty-state')).containsText('Try another name, code, team or department.')
        assertThat(page.locator('.toolbar .summary')).hasText('0 products in 0 departments')

        when:
        button('Clear the search', true).click()

        then:
        assertThat(search()).hasValue('')
        assertThat(names()).hasText(['CertScanner', 'Payments Hub'] as String[])
        assertThat(departmentNames()).hasText(DEPARTMENTS as String[])
        api.lastRequest('GET', '/api/products').params() == [:]
        ownErrors().isEmpty()
    }

    def "a product opens from its row or its name and the breadcrumb leads back"() {
        given:
        open('/admin/products')

        when:
        gridCell(row('Payments Hub'), 'ownerTeam').click()
        page.waitForURL('**/admin/products/2')

        then:
        assertThat(page.locator('h1')).hasText('Payments Hub')
        assertThat(page.locator('.breadcrumb > :not(.sep)'))
                .hasText(['DevSecOps Admin', 'Products', 'Fund Services', 'Payments Hub'] as String[])
        assertThat(fact('Department')).hasText('Fund Services')
        assertThat(page.locator('.page-header .code')).hasText('PAYHUB')
        assertThat(holding(serviceCard('mobile-app').locator('.service-facts > div'), "dt:text-is('Build tool')")).containsText('Flutter')
        assertThat(serviceCard('gateway').locator('.repository a'))
                .hasAttribute('href', 'https://bitbucket.bbh.com/projects/PAY/repos/payhub-gateway')
        assertThat(fact('Services')).hasText('4')
        assertThat(fact('Pipelines')).hasText('6, 1 key invalidated')
        assertThat(page.locator('.stats')).hasCount(0)

        when:
        page.locator(".breadcrumb a:text-is('Products')").click()
        page.waitForURL('**/admin/products')
        row('CertScanner').locator('a.name').click()
        page.waitForURL('**/admin/products/1')

        then:
        assertThat(page.locator('h1')).hasText('CertScanner')
        assertThat(pipelineTypes('gui')).hasText(['Full pipeline', 'SAST scanning pipeline'] as String[])
        assertThat(link('View monitoring', true)).hasAttribute('href', '/monitoring/products/1')
        assertThat(pipelineRow('gui', 'Full').getByRole(LINK,
                new Locator.GetByRoleOptions().setName('Open the full pipeline in Jenkins').setExact(true)))
                .hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')
        assertThat(pipelineRow('gui', 'Full').locator('.pipeline-actions').getByRole(LINK)).hasCount(1)
        assertThat(pipelineRow('gui', 'Full').locator('.pipeline-actions').getByRole(BUTTON)).hasCount(1)
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
