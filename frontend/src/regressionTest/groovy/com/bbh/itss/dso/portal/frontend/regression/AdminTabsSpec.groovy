package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Locator

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class AdminTabsSpec extends DsoSpecification {

    static final Map<String, String> TABS = [Departments       : '/admin/departments', Products: '/admin/products',
                                             'Service template': '/admin/template', 'Library defaults': '/admin/settings']

    def "the Admin page shows its heading, its description and the four tabs with the open one marked"() {
        when:
        open('/admin/departments')

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Admin')
        assertThat(page.locator('.page-header .page-description'))
                .hasText('Set up the portal for everyone: departments, products and their services, what a new service gets, and the settings every pipeline shares.')
        assertThat(tabs()).hasText(TABS.keySet() as String[])
        TABS.every { label, path ->
            assertThat(tab(label)).hasAttribute('href', path)
            true
        }
        assertThat(page.locator('nav.tab-bar')).hasAttribute('aria-label', 'DevSecOps Admin')
        isOpen('Departments')
        assertThat(page).hasTitle('Departments · DevSecOps Admin · BBH DevSecOps Management Portal')
        assertThat(activeMenuLink()).hasText('Admin')
        ownErrors().isEmpty()
    }

    def "the tabs switch between the departments, the products and the library defaults, and Back returns"() {
        given:
        open('/admin/products')

        when:
        tab('Departments').click()
        page.waitForURL('**/admin/departments')

        then:
        isOpen('Departments')
        assertThat(gridRows().locator('.name').first()).hasText('AI Lab')
        assertThat(page).hasTitle(~'^Departments · DevSecOps Admin')

        when:
        tab('Library defaults').click()
        page.waitForURL('**/admin/settings')

        then:
        isOpen('Library defaults')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins.bbh.com')
        assertThat(page).hasTitle(~'^Library defaults · DevSecOps Admin')

        when:
        tab('Products').click()
        page.waitForURL('**/admin/products')

        then:
        isOpen('Products')
        assertThat(page.locator('section.department h2').first()).hasText('AI Lab')

        when:
        page.goBack()
        page.waitForURL('**/admin/settings')

        then:
        isOpen('Library defaults')
        assertThat(page.locator('h1')).hasText('DevSecOps Admin')
        ownErrors().isEmpty()
    }

    def "the Admin page opens on the products tab from its address and from the menu"() {
        when:
        open('/admin')

        then:
        page.waitForURL('**/admin/products')
        isOpen('Products')

        when:
        open('/monitoring')
        menuLink('Admin').click()

        then:
        page.waitForURL('**/admin/products')
        isOpen('Products')
        assertThat(page.locator('h1')).hasText('DevSecOps Admin')
        ownErrors().isEmpty()
    }

    def "#path opens again on its tab when the page is reloaded"() {
        given:
        open(path)

        when:
        page.reload()

        then:
        assertThat(page).hasURL(url(path))
        assertThat(page.locator('h1')).hasText('DevSecOps Admin')
        isOpen(label)
        assertThat(page.locator(content).first()).isVisible()
        ownErrors().isEmpty()

        where:
        label              | content
        'Departments'      | '.ag-row'
        'Products'         | 'section.department'
        'Service template' | '.example'
        'Library defaults' | '.toc'
        path = TABS[label]
    }

    def "leaving the library defaults with unsaved changes through a tab asks first, and only Discard leaves"() {
        given:
        open('/admin/settings')

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        tab('Departments').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')
        assertThat(dialog()).containsText('The changes on this page have not been saved.')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        page.url().endsWith('/admin/settings')
        isOpen('Library defaults')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins2.bbh.com/')
        assertThat(page.locator('.save-bar')).containsText('Unsaved changes')

        when:
        tab('Products').click()
        dialogButton('Discard').click()
        page.waitForURL('**/admin/products')

        then:
        isOpen('Products')
        api.requests('PUT', '/api/settings').isEmpty()

        when:
        tab('Library defaults').click()
        page.waitForURL('**/admin/settings')
        tab('Departments').click()
        page.waitForURL('**/admin/departments')

        then:
        assertThat(dialog()).hasCount(0)
        ownErrors().isEmpty()
    }

    Locator tabs() {
        page.locator('nav.tab-bar a')
    }

    void isOpen(String label) {
        assertThat(page.locator('nav.tab-bar a.active')).hasText(label)
        assertThat(tab(label)).hasAttribute('aria-current', 'page')
        assertThat(page.locator('nav.tab-bar a[aria-current]')).hasCount(1)
    }
}
