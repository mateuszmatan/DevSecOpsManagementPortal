package com.bbh.itss.dso.portal.frontend.regression

import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class ServiceTemplateSpec extends EditorSpecification {

    def "an admin changes the template of a new service and sees what a service gets from it"() {
        when:
        open('/admin/products')
        tab('Service template').click()
        page.waitForURL('**/admin/template')

        then:
        assertThat(page).hasTitle(~'^Service template · DevSecOps Admin')
        assertThat(page.locator('.save-bar .saved')).containsText('(version 2)')
        assertThat(page.locator('.section h2'))
                .hasText(['Pipelines', 'Build', 'Nexus IQ and Bitbucket', 'OpenShift', 'What a new service gets'] as String[])
        assertThat(page.locator('.example .placeholders dt')).hasText(['{CODE}', '{code}', '{service}', '{type}'] as String[])
        hasValues(page.locator('form'), ['Jenkins agents'      : 'linux-agent',
                                         'Jenkins job'         : 'DevSecOps/{CODE}/{service}-{type}',
                                         'Gradle tasks'        : 'clean build', 'Maven goals': 'clean verify',
                                         'OpenShift project'   : '{code}-{service}',
                                         'Image registry'      : 'docker-qc.tools.bbh.com'])
        assertThat(example('Full pipeline job')).hasText('DevSecOps/CERT/backend-api-full')
        assertThat(example('OpenShift projects'))
                .hasText('cert-backend-api-build, cert-backend-api-rd, cert-backend-api-qc')

        when:
        fillIn(page.locator('form'), ['Jenkins job'      : 'Teams/{CODE}/{service}/{type}/{branch}',
                                      'OpenShift project': 'bbh-{code}-{service}'])
        button('Save template', true).click()

        then:
        assertThat(errorOf(page.locator('form'), 'Jenkins job'))
                .hasText('Unknown placeholder {branch}: use {CODE}, {code}, {service}, {type}')
        assertThat(saveError()).hasText('Some fields need your attention.')
        api.requests('PUT', '/api/service-template').isEmpty()

        when:
        input(page.locator('form'), 'Jenkins job').fill('Teams/{CODE}/{service}/{type}')

        then:
        assertThat(example('Full pipeline job')).hasText('Teams/CERT/backend-api/full')
        assertThat(example('OpenShift projects'))
                .hasText('bbh-cert-backend-api-build, bbh-cert-backend-api-rd, bbh-cert-backend-api-qc')

        when:
        button('Save template', true).click()

        then:
        assertThat(snackBar()).containsText('Service template saved. New services and pipelines get these values from now on.')
        assertThat(page.locator('.save-bar .saved')).containsText('(version 3)')
        with(awaitRequest('PUT', '/api/service-template').json()) {
            version == 2
            jenkinsJob == 'Teams/{CODE}/{service}/{type}'
            openShiftProject == 'bbh-{code}-{service}'
            agentLabels == ['linux-agent']
            healthCheckUrl == '/actuator/health'
        }

        when:
        open('/admin/products/1')
        buttonIn(holding(page.locator('section.service'), "h2:text-is('gui')"), 'Add pipeline', false).click()

        then:
        assertThat(input(dialog(), 'Jenkins job')).hasValue('Teams/CERTSCANNER/gui/security')
        ownErrors().isEmpty()
    }

    def "a template saved by someone else in the meantime is not overwritten"() {
        given:
        api.respond('PUT', '/api/service-template',
                problem(409, 'Conflict', 'The service template was changed by someone else'))

        when:
        open('/admin/template')
        input(page.locator('form'), 'Health check path').fill('/health')
        button('Save template', true).click()

        then:
        assertThat(page.locator('.conflict')).containsText('Someone else saved the template after you opened this page.')
        assertThat(saveError()).hasText('Not saved: the template was changed by someone else.')
        assertThat(button('Save template', true)).isDisabled()

        when:
        button('Reload', true).click()

        then:
        assertThat(page.locator('.conflict')).hasCount(0)
        assertThat(input(page.locator('form'), 'Health check path')).hasValue('/actuator/health')
    }

    Locator example(String label) {
        holding(page.locator('.example .pairs > div'), "dt:text-is('${label}')").locator('dd')
    }
}
