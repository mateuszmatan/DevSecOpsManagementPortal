package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.MENUITEM

abstract class ProductPageSpecification extends DsoSpecification {

    static final List<String> DEPARTMENTS = ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services']

    Locator departmentNames() {
        page.locator('section.department h2')
    }

    Locator department(String name) {
        holding(page.locator('section.department'), "h2:text-is('${name}')")
    }

    Locator serviceCard(String name) {
        holding(page.locator('section.service'), "h2:text-is('${name}')")
    }

    Locator pipelineRow(String service, String type) {
        holding(serviceCard(service).locator('.pipeline'), ".pipeline-title strong:text-is('${type} pipeline')")
    }

    Locator pipelineTypes(String service) {
        serviceCard(service).locator('.pipeline-title strong')
    }

    Locator fact(String label) {
        holding(page.locator('.page-header .facts > div'), "dt:text-is('${label}')").locator('dd')
    }

    void productAction(String action) {
        buttonIn(page.locator('.page-header'), 'More actions of', false).click()
        menuItem(action).click()
    }

    Locator pipelineButton(String service, String type, String name) {
        buttonIn(pipelineRow(service, type), name)
    }

    Locator keyOf(String service, String type) {
        pipelineRow(service, type).locator('.key-value')
    }

    void pipelineAction(String service, String type, String action) {
        buttonIn(pipelineRow(service, type), 'More actions of the', false).click()
        menuItem(action).click()
    }

    Locator menuItem(String name) {
        page.getByRole(MENUITEM, new Page.GetByRoleOptions().setName(name).setExact(true))
    }

    void keyRows(List<List<String>> expected) {
        expected.eachWithIndex { parts, index ->
            parts.each { assertThat(gridRows(dialog()).nth(index)).containsText(it) }
        }
    }
}
