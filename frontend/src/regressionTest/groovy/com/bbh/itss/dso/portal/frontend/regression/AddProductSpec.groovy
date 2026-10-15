package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.ProductStore
import com.microsoft.playwright.Locator
import groovy.json.JsonSlurper

import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.LINK

class AddProductSpec extends EditorSpecification {

    static final String GUI_APPLICATION = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b31'
    static final String API_APPLICATION = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b32'
    static final String TAKEN_APPLICATION = '209f44ac-dd06-4ca0-884e-d944904f8020'

    def "a new product starts from its department and name, and its code follows the name until the code is typed"() {
        when:
        open('/admin/products/new')
        dialogButton('Cancel').click()
        page.waitForURL('**/admin/products')

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Admin')

        when:
        open('/admin/products/new')
        dialogButton('Continue').click()

        then:
        assertThat(errorOf(dialog(), 'Department')).hasText('Required')
        assertThat(errorOf(dialog(), 'Product name')).hasText('Required')

        when:
        choose(dialog(), 'Department', 'Fund Services')
        input(dialog(), 'Product name').fill('Cert Scanner')
        dialogButton('Continue').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(selected(productFields(), 'Department')).hasText('Fund Services')
        assertThat(input(productFields(), 'Name')).hasValue('Cert Scanner')
        assertThat(input(productFields(), 'Code')).hasValue('CERTSCANNER')

        when:
        input(productFields(), 'Name').fill('Cert Scanner Next')

        then:
        assertThat(input(productFields(), 'Code')).hasValue('CERTSCANNERNEXT')

        when:
        input(productFields(), 'Code').fill('CERTNEXT')
        input(productFields(), 'Name').fill('Cert Scanner Two')
        page.waitForTimeout(600)

        then:
        assertThat(input(productFields(), 'Code')).hasValue('CERTNEXT')
        api.requests('GET', '/api/products/code-suggestion')*.params()*.name == ['Cert Scanner', 'Cert Scanner Next']
        ownErrors().isEmpty()
    }

    def "Add a product to an empty department starts the new product in that department"() {
        when:
        open('/admin/products')
        holding(page.locator('section.department'), "h2:text-is('Custody')")
                .getByRole(LINK, new Locator.GetByRoleOptions().setName('Add a product to Custody').setExact(true)).click()

        then:
        assertThat(page).hasURL(~'/admin/products/new\\?department=4$')
        assertThat(selected(dialog(), 'Department')).hasText('Custody')

        when:
        input(dialog(), 'Product name').fill('Trade Archive')
        dialogButton('Continue').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(selected(productFields(), 'Department')).hasText('Custody')
        assertThat(input(productFields(), 'Code')).hasValue('TRADEARCHIVE')
        ownErrors().isEmpty()
    }

    def "an empty product is refused in the browser and names every required field"() {
        given:
        startProduct('CertScanner Next')
        input(productFields(), 'Code').clear()
        input(productFields(), 'Name').clear()

        when:
        button('Add product', true).click()

        then:
        assertThat(saveError()).hasText('Some fields need your attention.')
        ['Code', 'Name', 'API key ID'].every { label ->
            assertThat(errorOf(productFields(), label)).hasText('Required')
            true
        }
        assertThat(servicePanel('new-service').locator('.tag.error')).hasText('Needs attention')
        assertThat(errorOf(openService(), 'Service name')).hasText('Required')
        assertThat(openService().locator('.rail-item.problem > span:first-of-type'))
                .hasText(['General', 'Build', 'AppScan SAST and DAST'] as String[])

        when:
        showSection('Build')

        then:
        assertThat(errorOf(openService(), 'JDK path')).hasText('Required')
        assertThat(input(openService(), 'Gradle tasks')).hasValue('clean build')
        assertThat(errorOf(openService(), 'Gradle tasks')).hasCount(0)

        when:
        showSection('AppScan SAST and DAST')

        then:
        assertThat(errorOf(openService(), 'AppScan application ID')).hasText('Required')
        api.requests('POST', '/api/products').isEmpty()
        ownErrors().isEmpty()
    }

    def "a new service takes the template build of the build tool chosen for it and keeps what was typed"() {
        given:
        startProduct('CertScanner Next')
        showSection('Build')

        expect:
        hasValues(openService(), ['Gradle tasks': 'clean build', 'Artifact path': 'build/libs/*.jar'])

        when:
        choose(openService(), 'Build tool', 'Maven')

        then:
        hasValues(openService(), ['Maven goals': 'clean verify', 'Artifact path': 'target/*.jar'])

        when:
        input(openService(), 'Artifact path').fill('target/cert.jar')
        choose(openService(), 'Build tool', 'Gradle')

        then:
        hasValues(openService(), ['Gradle tasks': 'clean build', 'Artifact path': 'target/cert.jar'])
        ownErrors().isEmpty()
    }

    def "a product with two services is added after the server's field errors are corrected"() {
        given:
        api.respond('POST', '/api/products', problem(400, 'Bad Request', 'Some values are not valid', [errors: [
                [field: 'code', message: 'another product already uses this code'],
                [field: 'services[1].appScan.applicationId', message: 'the AppScan application belongs to CertScanner'],
                [field: 'version', message: 'must be empty for a new product']]]))
        startProduct('CertScanner Next')

        when:
        input(productFields(), 'Code').clear()
        input(productFields(), 'Code').pressSequentially('cert-2')
        fillIn(productFields(), ['Name'         : 'CertScanner Next', 'Owner team': 'Technology Architecture',
                                 'Description'  : 'The next generation of the certificate scanner',
                                 'Contact e-mail': 'ta-team@bbh.com', 'Secret text credentials ID': 'hcl-app-scan-account',
                                 'API key ID'   : 'bbh_5f0c2a9e-1b7d-4c3e-8a6f-2d9b0e4c7a15'])

        then:
        assertThat(input(productFields(), 'Code')).hasValue('CERT-2')

        when:
        fillIn(openService(), ['Service name': 'gui', 'Description': 'Angular front end'])
        showSection('Build')
        fillIn(openService(), ['JDK path'     : '/usr/lib/jvm/java-21-openjdk', 'Artifact path': 'build/libs/*.jar',
                               'Gradle tasks': 'clean build'])
        showSection('AppScan SAST and DAST')
        input(openService(), 'AppScan application ID').fill(GUI_APPLICATION)
        button('Add service', true).click()

        then:
        assertThat(serviceNames()).hasText(['gui', 'new-service'] as String[])
        assertThat(openService().locator('.service-name')).hasText('new-service')

        when:
        input(openService(), 'Service name').fill('backend-api')
        showSection('Build')
        choose(openService(), 'Build tool', 'Maven')
        fillIn(openService(), ['JDK path'    : '/usr/lib/jvm/java-21-openjdk', 'Artifact path': 'target/*.jar',
                               'Maven goals': 'clean verify'])
        showSection('Deployment')
        input(openService(), 'Maven goals').fill('deploy')
        showSection('AppScan SAST and DAST')
        input(openService(), 'AppScan application ID').fill(TAKEN_APPLICATION)
        button('Add product', true).click()

        then:
        assertThat(saveError()).hasText('The portal did not accept some values. They are marked below.')
        assertThat(errorOf(productFields(), 'Code')).hasText('another product already uses this code')
        assertThat(openService().locator('.service-name')).hasText('backend-api')
        assertThat(servicePanel('backend-api').locator('.tag.error')).hasText('Needs attention')
        assertThat(errorOf(openService(), 'AppScan application ID')).hasText('the AppScan application belongs to CertScanner')
        assertThat(page.locator('.banner .problems li')).hasText(['version: must be empty for a new product'] as String[])
        api.requests('POST', '/api/products').size() == 1

        when:
        def store = ProductStore.created(api, 3)
        input(productFields(), 'Code').fill('CERTNEXT')
        input(openService(), 'AppScan application ID').fill(API_APPLICATION)

        then:
        assertThat(errorOf(productFields(), 'Code')).hasCount(0)
        assertThat(errorOf(openService(), 'AppScan application ID')).hasCount(0)

        when:
        button('Add product', true).click()
        page.waitForURL('**/admin/products/3')

        then:
        api.requests('POST', '/api/products').size() == 2
        api.lastRequest('POST', '/api/products').json() == expected('new-product-request.json')
        assertThat(page.locator('h1')).hasText('CertScanner Next')
        assertThat(snackBar()).containsText('CertScanner Next added. Each of its services got a Full pipeline with its own key.')
        assertThat(page.locator('.generated')).containsText('Pipeline keys generated for 2 new services: gui, backend-api.')
        store.generatedKeys.keySet() == ['gui', 'backend-api'] as Set
        store.generatedKeys.values().every { key ->
            assertThat(holdingText(page.locator('.key-value'), key as String)).isVisible()
            true
        }
        assertThat(page.locator('.service .tag.new')).hasCount(2)
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    static Object expected(String name) {
        new JsonSlurper().parse(AddProductSpec.getResource(name))
    }
}
