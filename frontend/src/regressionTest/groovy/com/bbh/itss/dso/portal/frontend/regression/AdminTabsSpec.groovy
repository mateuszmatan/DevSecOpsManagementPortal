package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Locator

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class AdminTabsSpec extends DsoSpecification {

    static final Map<String, String> TABS = ['Business Services': '/admin/departments', Products: '/admin/products',
                                             'Service template' : '/admin/template', 'Library defaults': '/admin/settings']

    def "the Admin page shows its heading, its description and the four tabs with the open one marked"() {
        when:
        open('/admin/departments')

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Admin')
        assertThat(page.locator('.page-header .page-description'))
                .hasText('Set up the portal for everyone: business services, products and their services, what a new service gets, and the settings every pipeline shares.')
        assertThat(tabs()).hasText(TABS.keySet() as String[])
        TABS.every { label, path ->
            assertThat(tab(label)).hasAttribute('href', path)
            true
        }
        assertThat(page.locator('nav.tab-bar')).hasAttribute('aria-label', 'DevSecOps Admin')
        isOpen('Business Services')
        assertThat(page).hasTitle('Business Services · DevSecOps Admin · BBH DevSecOps Management Portal')
        assertThat(activeMenuLink()).hasText('Admin')
        ownErrors().isEmpty()
    }

    def "the tabs switch between the departments, the products and the library defaults, and Back returns"() {
        given:
        open('/admin/products')

        when:
        tab('Business Services').click()
        page.waitForURL('**/admin/departments')

        then:
        isOpen('Business Services')
        assertThat(gridRows().locator('.name').first()).hasText('AI Lab')
        assertThat(page).hasTitle(~'^Business Services · DevSecOps Admin')

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

    Locator tabs() {
        page.locator('nav.tab-bar a')
    }

    void isOpen(String label) {
        assertThat(page.locator('nav.tab-bar a.active')).hasText(label)
        assertThat(tab(label)).hasAttribute('aria-current', 'page')
        assertThat(page.locator('nav.tab-bar a[aria-current]')).hasCount(1)
    }
}
