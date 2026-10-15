package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Locator

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

abstract class EditorSpecification extends DsoSpecification {

    void startProduct(String name, String department = 'Corporate Technology') {
        open('/admin/products/new')
        choose(dialog(), 'Department', department)
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

    void showAdvancedSettings() {
        openService().locator('dso-advanced-settings .accordion-header button').click()
        assertThat(openService().locator('dso-advanced-settings dso-panel')).hasClass(~/\bexpanded\b/)
    }
}
