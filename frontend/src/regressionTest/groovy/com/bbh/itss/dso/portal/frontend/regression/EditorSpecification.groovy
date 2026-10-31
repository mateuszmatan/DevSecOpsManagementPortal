package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

abstract class EditorSpecification extends DsoSpecification {

    /** The Library defaults (SQL and PL/SQL) that the service forms read: the allowed databases and the repositories. */
    static final Map DATABASE_DEFAULTS = [allowedTargets       : ['dev-db.bbh.com', 'rd-db.bbh.com', 'qc-db.bbh.com'],
                                          snapshotRepositoryId : 'bbh-db-snapshots',
                                          snapshotRepositoryUrl: 'https://tools.bbh.com/nexus/repository/db-snapshots',
                                          releaseRepositoryId  : 'bbh-db-releases',
                                          releaseRepositoryUrl : 'https://tools.bbh.com/nexus/repository/db-releases']

    void allowDatabases() {
        api.respond('GET', '/api/settings', (fixture('settings.json') as Map) + [database: DATABASE_DEFAULTS])
    }

    void startProduct(String name, String department = 'Corporate Technology') {
        open('/admin/products/new')
        choose(dialog(), 'Business Service', department)
        input(dialog(), 'Product name').fill(name)
        dialogButton('Continue').click()
        assertThat(dialog()).hasCount(0)
        assertThat(input(productFields(), 'Name')).hasValue(name)
    }

    Locator productFields() {
        page.locator('.product-fields')
    }

    Locator openService() {
        page.locator('dso-panel.service-panel.expanded')
    }

    Locator servicePanel(String name) {
        holding(page.locator('dso-panel.service-panel'), ".service-name:text-is('${name}')")
    }

    Locator serviceNames() {
        page.locator('dso-panel.service-panel .service-name')
    }

    Locator sectionNames() {
        openService().locator('.rail-item > span:not(.problem-mark)')
    }

    /** The block of the test, RD or QC database under Database changes. */
    Locator databaseBlock(String target, Locator scope = openService()) {
        scope.locator(".form-fields.block:has(> h4 > code:text-is('database.${target}'))")
    }

    void expandService(String name) {
        expand(servicePanel(name))
    }

    void expandServiceAt(int index) {
        expand(page.locator('dso-panel.service-panel').nth(index))
    }

    private void expand(Locator panel) {
        panel.locator('.accordion-header button').first().click()
        assertThat(panel).hasClass(~/\bexpanded\b/)
        assertThat(openService()).hasCount(1)
    }

    void showSection(String label) {
        openService().locator(".rail-item:has(> span:text-is('${label}'))").click()
        assertThat(openService().locator('.pane-header h3')).hasText(label)
    }

    void addUrbanCodeApplication(String name, String baseDir) {
        showSection('UrbanCode Deploy')
        buttonIn(openService(), 'Add application').click()
        fillIn(openService(), ['Application name': name, 'Component name': "$name-app".toString(),
                               'Base folder'     : baseDir, 'Files to include': '*.jar'])
    }

    void showAdvancedSettings() {
        openService().locator('dso-advanced-settings .accordion-header button').click()
        assertThat(openService().locator('dso-advanced-settings dso-panel')).hasClass(~/\bexpanded\b/)
    }
}
