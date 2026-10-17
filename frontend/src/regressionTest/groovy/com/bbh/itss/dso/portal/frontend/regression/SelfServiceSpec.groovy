package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.ProductStore
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON

class SelfServiceSpec extends EditorSpecification {

    static final String API_APPLICATION = '4b1c2d3e-1111-4a5b-8c9d-0e1f2a3b4c5d'
    static final String GUI_APPLICATION = '4b1c2d3e-2222-4a5b-8c9d-0e1f2a3b4c5d'
    static final String APP_SCAN_KEY = 'bbh_9a1b2c3d-0000-4abc-9def-123456789abc'
    static final String SAST_KEY_OF_GUI = '2c0ca4f4-a1a6-472a-9685-0c75f22fe713'
    static final String SAST = 'SAST (Static Application Security Tests) - HCL AppScan'
    static final String OSA = 'OSA (Open Source Analysis) (NexusIQ with Golden Fix and Golden Pull Request)'

    def "a product manager adds a new product with a Security pipeline step by step"() {
        given:
        recordClipboard()
        def store = ProductStore.created(api, 3)

        when:
        open('/monitoring')
        menuLink('Self-service').click()
        page.waitForURL('**/self-service')
        input(step(), 'Product name').fill('CertScanner')
        button('Next: Pipeline', true).click()

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Self-service')
        assertThat(page.locator('.step-bar .step-label'))
                .hasText(['Product', 'Pipeline', 'Services', 'Review', 'Next steps'] as String[])
        assertThat(currentStep()).hasText('Product')
        assertThat(radio(step(), 'A new product')).hasAttribute('aria-checked', 'true')
        hasErrors(step(), ['Department'        : 'Required',
                           'Product name'      : 'This product is already in the portal: choose "A product in the portal" above',
                           'AppScan API key ID': 'Required'])

        when:
        choose(step(), 'Department', 'Custody')
        fillIn(step(), ['Product name': 'Trade Archive', 'Owner team': 'Custody Technology',
                        'AppScan API key ID': APP_SCAN_KEY])

        then:
        assertThat(hintOf(step(), 'Product name')).hasText('Its product code will be TRADEARCHIVE, the short name used in job names')

        when:
        button('Next: Pipeline', true).click()
        button('Next: Services', true).click()

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('h2')).hasText('Which pipeline does Trade Archive need?')
        assertThat(step().locator('.tile-label')).hasText([SAST, OSA, 'Security', 'Full'] as String[])
        assertThat(choiceError()).hasText('Choose a pipeline to continue')

        when:
        radio(step(), 'Security').click()
        button('Next: Services', true).click()
        button('Next: Review', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Which services does Trade Archive have?')
        assertThat(choiceError()).hasText('Add at least one service')

        when:
        button('Add a service', true).click()
        fillIn(dialog(), ['Service name': 'archive-api', 'Description': 'REST API of the archive'])
        showDialogSection('Build')
        choose(dialog(), 'Build tool', 'Maven')
        showDialogSection('AppScan SAST and DAST')
        input(dialog(), 'AppScan application ID').fill(API_APPLICATION)
        dialogButton('Add service').click()
        addService('archive-gui', GUI_APPLICATION, 'Gradle')

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(step().locator('.service-list strong')).hasText(['archive-api', 'archive-gui'] as String[])
        assertThat(serviceRow('archive-api')).containsText('REST API of the archive')

        when:
        button('Next: Review', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(review('Department')).hasText('Custody')
        assertThat(review('Pipeline')).hasText('Security')

        when:
        button('Create the pipelines', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Trade Archive is saved. Now do these steps in order')
        def request = awaitRequest('POST', '/api/products', 1)
        request.params() == [pipelineType: 'SECURITY']
        with(request.json() as Map) {
            code == 'TRADEARCHIVE'
            name == 'Trade Archive'
            departmentId == 4
            ownerTeam == 'Custody Technology'
            appScan.keyId == SelfServiceSpec.APP_SCAN_KEY
            services*.name == ['archive-api', 'archive-gui']
            services*.appScan*.applicationId == [SelfServiceSpec.API_APPLICATION, SelfServiceSpec.GUI_APPLICATION]
            services*.build*.tool == ['MAVEN', 'GRADLE']
        }
        store.services*.pipelines*.type == [['SECURITY'], ['SECURITY']]
        assertThat(page.locator('.jenkinsfile .code-block')).hasText(store.services.collect { service ->
            "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys[service.serviceName]}')".toString()
        } as String[])
        assertThat(link('Open Trade Archive in DevSecOps Admin', true)).hasAttribute('href', '/admin/products/3')

        when:
        button('Copy the Jenkinsfile of archive-api', true).click()

        then:
        assertThat(snackBar()).containsText('Jenkinsfile copied to the clipboard')
        copiedTexts() == ["@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys['archive-api']}')\n".toString()]
        ownErrors().isEmpty()
    }

    def "a product in the portal gets a SAST pipeline next to the pipelines it has, a new service included"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def stored = fixture('product-1.json') as Map

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('.today li')).hasText(['gui · Full, SAST', 'backend-api · Full'] as String[])

        when:
        radio(step(), SAST).click()
        button('Next: Services', true).click()
        buttonIn(serviceRow('gui'), 'Change gui').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Change gui')

        when:
        input(dialog(), 'Description').fill('Web front end')
        dialogButton('Save service').click()
        addService('scanner', API_APPLICATION, 'Maven')

        then:
        assertThat(step().locator('.service-list strong')).hasText(['gui', 'backend-api', 'scanner'] as String[])
        assertThat(step().locator('.service-list .tag')).hasText(['Changed', 'New'] as String[])

        when:
        button('Next: Review', true).click()
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is saved. Now do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'SAST']
        with(request.json() as Map) {
            version == stored.version
            departmentId == stored.departmentId
            services*.id == [1, 2, null]
            services*.description == ['Web front end', stored.services[1].description, null]
            services[2].build.tool == 'MAVEN'
        }
        store.services*.pipelines*.type == [['FULL', 'SAST'], ['FULL', 'SAST'], ['SAST']]
        store.generatedKeys.keySet() == ['backend-api', 'scanner'] as Set
        assertThat(page.locator('.jenkinsfile .code-block')).containsText([SAST_KEY_OF_GUI, store.generatedKeys['backend-api'],
                                                                    store.generatedKeys['scanner']] as String[])

        when:
        button('Set up another product', true).click()

        then:
        assertThat(currentStep()).hasText('Product')
        assertThat(radio(step(), 'A new product')).hasAttribute('aria-checked', 'true')
        assertThat(input(step(), 'Product name')).hasValue('')
        ownErrors().isEmpty()
    }

    void openProduct(String department, String product) {
        open('/self-service')
        choose(step(), 'Department', department)
        radio(step(), 'A product in the portal').click()
        choose(step(), 'Product', product)
        assertThat(step().locator('dl.rows')).isVisible()
        button('Next: Pipeline', true).click()
    }

    void showDialogSection(String label) {
        dialog().locator(".rail-item:has(> span:text-is('${label}'))").click()
    }

    void addService(String name, String applicationId, String tool) {
        button('Add a service', true).click()
        input(dialog(), 'Service name').fill(name)
        showDialogSection('Build')
        choose(dialog(), 'Build tool', tool)
        showDialogSection('AppScan SAST and DAST')
        input(dialog(), 'AppScan application ID').fill(applicationId)
        dialogButton('Add service').click()
    }

    @Override
    Locator hintOf(Locator scope, String label) {
        formField(scope, label).locator('dso-hint')
    }

    Locator step() {
        page.locator('section.step')
    }

    Locator currentStep() {
        page.locator('.step-bar .current .step-label')
    }

    Locator choiceError() {
        step().locator('.choice-error')
    }

    Locator review(String term) {
        step().locator("dl.rows dt:text-is('${term}') + dd")
    }

    Locator serviceRow(String name) {
        holding(step().locator('.service-list li'), "strong:text-is('${name}')")
    }
}
