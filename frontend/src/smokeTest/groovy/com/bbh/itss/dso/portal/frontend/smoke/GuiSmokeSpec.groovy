package com.bbh.itss.dso.portal.frontend.smoke

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Page
import spock.lang.IgnoreIf

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class GuiSmokeSpec extends DsoSpecification {

    static final List<Map<String, String>> SECTIONS = [
            [label      : 'Pipelines', heading: 'DevSecOps Pipelines', path: '/pipelines',
             description: 'Every automated build, test and security pipeline of your department\'s products. Open one to see its key, its Jenkinsfile and its latest runs.'],
            [label      : 'Self-service', heading: 'DevSecOps Self-service', path: '/self-service',
             description: 'Set up the DevSecOps pipelines of your product, or change them, in five guided steps. No DevSecOps knowledge needed.'],
            [label      : 'Pipeline Monitoring', heading: 'DevSecOps Pipeline Monitoring', path: '/monitoring',
             description: 'How the pipelines of every product are doing: whether their latest runs passed, and how often and how safely changes reach production.'],
            [label      : 'Admin', heading: 'DevSecOps Admin', path: '/admin/products',
             description: 'Set up the portal for everyone: departments, products and their services, what a new service gets, and the settings every pipeline shares.']]

    static final Map<String, String> ADMIN_TABS = [Departments       : '/admin/departments', Products: '/admin/products',
                                                   'Service template': '/admin/template', 'Library defaults': '/admin/settings']

    static final String REGENERATED_KEY = '3f9d2c4e-8a1b-4c7d-9e2f-5b6a7c8d1e04'

    static final String GENERATED_KEY = '9c4e1a7b-2d3f-4e5a-8b6c-7d8e9f0a1b2c'

    static final String CORPORATE_TECHNOLOGY = "localStorage.setItem('dso.beadle.department', '3')"

    def "the portal shows its title, only the DevSecOps sections in its menu and the footer"() {
        when:
        open('/admin/products')

        then:
        assertThat(page.locator('header .brand-name')).hasText('BBH DevSecOps Management Portal')
        assertThat(menuLinks()).hasText(MENU as String[])
        assertThat(activeMenuLink()).hasText('Admin')
        !page.locator('header.topbar').textContent().contains('Beadle')
        SECTIONS.every { section -> !page.locator('header.topbar').textContent().contains(section.description) }
        assertThat(page.locator('footer')).containsText('BBH 2026')
        ownErrors().isEmpty()
    }

    def "the product list groups the products by department with the tally of their pipelines"() {
        when:
        open('/admin/products')

        then:
        assertThat(page.locator('section.department h2').first()).isVisible()
        assertThat(page.locator('section.department .tally').first())
                .hasText(~/^\d+ products?, \d+ pipelines?(, \d+ keys? invalidated)?$/)
        assertThat(page.locator('.toolbar .summary'))
                .hasText(~/^\d+ products? in \d+ departments?(, \d+ not in a department)?$/)
        ownErrors().isEmpty()
    }

    def "every menu link opens its section with the full name and the description on top"() {
        given:
        open('/monitoring')

        expect:
        (SECTIONS.drop(2) + SECTIONS.take(2)).every { section ->
            menuLink(section.label).click()
            page.waitForURL("**${section.path}")
            assertThat(page.locator('h1')).hasText(section.heading)
            assertThat(page.locator('.page-header .page-description')).hasText(section.description)
            true
        }
        ownErrors().isEmpty()
    }

    def "#path opens from a direct link"() {
        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).containsText(heading)
        ownErrors().isEmpty()

        where:
        [path, heading] << SECTIONS.collect { [it.path, it.heading] } +
                ADMIN_TABS.values().collect { [it, 'DevSecOps Admin'] } +
                [['/admin', 'DevSecOps Admin'], ['/admin/products/new', 'Add product']]
    }

    def "every tab of DevSecOps Admin opens its page with the tab marked"() {
        given:
        open(ADMIN_TABS.values().first())

        expect:
        assertThat(page.locator('nav.tab-bar a')).hasText(ADMIN_TABS.keySet() as String[])
        ADMIN_TABS.every { label, path ->
            tab(label).click()
            page.waitForURL("**$path")
            assertThat(page.locator('h1')).hasText('DevSecOps Admin')
            assertThat(page.locator('nav.tab-bar a.active')).hasText(label)
            assertThat(tab(label)).hasAttribute('aria-current', 'page')
            assertThat(page.locator('.page.admin > router-outlet + *')).hasCount(1)
            true
        }
        ownErrors().isEmpty()
    }

    @IgnoreIf({ DsoSpecification.remoteBaseUrl() })
    def "#path shows #heading from the API"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).containsText(heading)
        ownErrors().isEmpty()

        where:
        path                       | heading
        '/pipelines'               | 'DevSecOps Pipelines'
        '/pipelines/1'             | 'Full pipeline'
        '/admin/products/1'        | 'CertScanner'
        '/admin/products/2/edit'   | 'Edit Payments Hub'
        '/monitoring/products/1'   | 'CertScanner'
        '/monitoring/pipelines/1'  | 'Full pipeline'
    }

    @IgnoreIf({ DsoSpecification.remoteBaseUrl() })
    def "#path shows no icons"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).isVisible()
        page.locator('svg-icon').count() == 0
        ownErrors().isEmpty()

        where:
        path << ['/pipelines', '/pipelines/1', '/self-service', '/monitoring', '/monitoring/products/1', '/monitoring/pipelines/1',
                 '/admin/departments', '/admin/products', '/admin/products/1', '/admin/template', '/admin/settings']
    }

    @IgnoreIf({ DsoSpecification.remoteBaseUrl() })
    def "the service editor keeps icons only in its vertical section menu"() {
        when:
        open('/admin/products/1/edit')
        page.locator('dso-panel .accordion-button').first().click()

        then:
        assertThat(page.locator('.rail svg-icon svg').first()).isVisible()
        page.locator('svg-icon').count() == page.locator('.rail svg-icon').count()
        ownErrors().isEmpty()
    }

    @IgnoreIf({ DsoSpecification.remoteBaseUrl() })
    def "#path fits a #width px window without horizontal scrolling"() {
        given:
        page.setViewportSize(width, 900)
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        (page.evaluate('() => document.documentElement.scrollWidth - document.documentElement.clientWidth') as int) == 0
        ownErrors().isEmpty()

        where:
        [path, width] << [['/pipelines', '/pipelines/1', '/self-service', '/monitoring', '/monitoring/products/1',
                           '/admin/departments', '/admin/products', '/admin/products/1', '/admin/products/1/edit',
                           '/admin/template', '/admin/settings'], [800, 600]].combinations()
    }

    @IgnoreIf({ DsoSpecification.remoteBaseUrl() })
    def "an invalidated pipeline key is regenerated with a visible text button"() {
        given:
        api.respond('POST', '/api/pipelines/9/keys', fixture('pipeline-9-regenerated.json'))

        when:
        open('/admin/products/2')
        def regenerate = button('Regenerate key of the SAST scanning pipeline')

        then:
        assertThat(regenerate).hasCount(1)
        assertThat(regenerate).isVisible()
        regenerate.locator('svg-icon').count() == 0

        when:
        regenerate.click()

        then:
        assertThat(page.locator('.key-value', new Page.LocatorOptions().setHasText(REGENERATED_KEY))).isVisible()
        assertThat(snackBar())
                .containsText('SAST scanning pipeline of mobile-app has a new key')
        assertThat(button('Regenerate key')).hasCount(0)
        api.requests('POST', '/api/pipelines/9/keys').size() == 1
        ownErrors().isEmpty()
    }

    @IgnoreIf({ DsoSpecification.remoteBaseUrl() })
    def "saving a new service shows the pipeline key generated for it on the product page"() {
        given:
        def saved = fixture('product-1.json') as Map
        def services = saved.services as List<Map>
        saved.services = [services[0], services[0] + [id: 7, name: 'gui-copy'], services[1]]
        saved.version = (saved.version as int) + 1
        api.on('PUT', '/api/products/1') { saved }
        api.get('/api/products/1') { productSaved() ? saved : fixture('product-1.json') }
        api.get('/api/products/1/pipelines') {
            fixture(productSaved() ? 'product-1-pipelines-with-new-service.json' : 'product-1-pipelines.json')
        }

        when:
        open('/admin/products/1/edit')
        page.locator('dso-panel .accordion-button').first().click()
        button('Duplicate').first().click()
        button('Save changes').click()
        page.waitForURL('**/admin/products/1')

        then:
        assertThat(page.locator('.generated')).containsText('Pipeline key generated for the new service gui-copy.')
        assertThat(page.locator('.key-value', new Page.LocatorOptions().setHasText(GENERATED_KEY))).isVisible()
        api.lastRequest('PUT', '/api/products/1').json().services*.id == [1, null, 2]
        ownErrors().isEmpty()
    }

    def "an unknown address falls back to pipeline monitoring"() {
        when:
        open('/no-such-page')

        then:
        page.waitForURL('**/monitoring')
        assertThat(page.locator('h1')).hasText('DevSecOps Pipeline Monitoring')
    }

    boolean productSaved() {
        !api.requests('PUT', '/api/products/1').isEmpty()
    }
}
