package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.ProductStore

import static com.bbh.itss.dso.portal.frontend.support.ApiData.noFlutterSettings
import static com.bbh.itss.dso.portal.frontend.support.ApiData.withoutResponseFields
import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class EditProductSpec extends EditorSpecification {

    def "saving product #id unchanged sends back exactly what was loaded, version included"() {
        given:
        def loaded = fixture("product-${id}.json") as Map
        ProductStore.recorded(api, id)
        open("/admin/products/$id/edit")

        when:
        button('Save changes', true).click()
        page.waitForURL("**/admin/products/$id")

        then:
        def body = awaitRequest('PUT', "/api/products/$id").json() as Map
        body == withoutResponseFields(loaded)
        body.version == loaded.version
        (loaded.services as List<Map>).findAll { (it.build as Map).tool != 'FLUTTER' }*.flutter.every { it == noFlutterSettings() }
        assertThat(snackBar()).containsText("${loaded.name} saved. Its pipelines get the new settings the next time they run.")
        ownErrors().isEmpty()

        where:
        id << [1, 2]
    }

    def "a product without a department is saved only once its department is chosen"() {
        given:
        def store = ProductStore.recorded(api, 1)
        store.product.departmentId = null
        open('/admin/products/1/edit')

        when:
        button('Save changes', true).click()

        then:
        assertThat(saveError()).hasText('Some fields need your attention.')
        assertThat(errorOf(productFields(), 'Department')).hasText('Required')
        api.requests('PUT', '/api/products/1').isEmpty()

        when:
        choose(productFields(), 'Department', 'Custody')
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/1')

        then:
        awaitRequest('PUT', '/api/products/1').json().departmentId == 4
        assertThat(page.locator('.breadcrumb > :not(.sep)'))
                .hasText(['DevSecOps Admin', 'Products', 'Custody', 'CertScanner'] as String[])
        ownErrors().isEmpty()
    }

    def "leaving the editor with unsaved changes asks first, and only Discard leaves"() {
        given:
        ProductStore.recorded(api, 1)
        open('/admin/products/1')
        link('Edit product', true).click()
        page.waitForURL('**/admin/products/1/edit')

        when:
        input(productFields(), 'Name').fill('CertScanner Renamed')
        menuLink('Pipeline Monitoring').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')
        assertThat(dialog()).containsText('The changes on this page have not been saved.')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        page.url().endsWith('/admin/products/1/edit')
        assertThat(input(productFields(), 'Name')).hasValue('CertScanner Renamed')
        assertThat(page.locator('.save-bar')).containsText('Unsaved changes')

        when:
        page.goBack()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialogButton('Discard').click()
        page.waitForURL('**/admin/products/1')

        then:
        assertThat(page.locator('h1')).hasText('CertScanner')
        api.requests('PUT', '/api/products/1').isEmpty()
        ownErrors().isEmpty()
    }

    def "an unchanged or saved editor is left without a question"() {
        given:
        ProductStore.recorded(api, 1)
        open('/admin/products/1/edit')

        when:
        button('Cancel', true).click()
        page.waitForURL('**/admin/products/1')

        then:
        assertThat(dialog()).hasCount(0)

        when:
        link('Edit product', true).click()
        input(productFields(), 'Owner team').fill('Platform Security')
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/1')
        menuLink('Pipelines').click()
        page.waitForURL('**/pipelines')

        then:
        assertThat(dialog()).hasCount(0)
        awaitRequest('PUT', '/api/products/1').json().ownerTeam == 'Platform Security'
        ownErrors().isEmpty()
    }

    def "services are moved, duplicated and removed, and the save sends them in the new order"() {
        given:
        def loaded = withoutResponseFields(fixture('product-2.json') as Map)
        def store = ProductStore.recorded(api, 2)
        open('/admin/products/2/edit')

        expect:
        assertThat(serviceNames()).hasText(['gateway', 'ledger', 'notifications', 'mobile-app'] as String[])

        when:
        expandService('ledger')
        button('Move ledger up', true).click()

        then:
        assertThat(serviceNames()).hasText(['ledger', 'gateway', 'notifications', 'mobile-app'] as String[])
        assertThat(openService().locator('.service-name')).hasText('ledger')
        assertThat(button('Move ledger up', true)).isDisabled()

        when:
        expandService('notifications')
        buttonIn(openService(), 'Duplicate service').click()

        then:
        assertThat(serviceNames()).hasText(['ledger', 'gateway', 'notifications', 'notifications-copy', 'mobile-app'] as String[])
        assertThat(openService().locator('.service-name')).hasText('notifications-copy')
        assertThat(servicePanel('notifications-copy').locator('.tag.new')).hasText('New')

        when:
        expandService('gateway')
        buttonIn(openService(), 'Remove service').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Remove the service gateway?')
        assertThat(dialog()).containsText("Saving the product deletes the service's 3 pipelines and keys.")

        when:
        dialogButton('Remove service').click()
        button('Add service', true).click()
        buttonIn(openService(), 'Remove service').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(serviceNames()).hasText(['ledger', 'notifications', 'notifications-copy', 'mobile-app'] as String[])

        when:
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/2')

        then:
        def body = awaitRequest('PUT', '/api/products/2').json() as Map
        def services = body.services as List<Map>
        def byName = (loaded.services as List<Map>).collectEntries { [(it.name): it] }
        services*.id == [4, 5, null, 6]
        services[0] == byName.ledger
        services[1] == byName.notifications
        services[3] == byName['mobile-app']
        services[2] == byName.notifications + [id     : null, name: 'notifications-copy',
                                                sonar  : (byName.notifications.sonar as Map) + [projectKey: null],
                                                metrics: (byName.notifications.metrics as Map) + [influxProject: null]]
        body.findAll { it.key != 'services' } == loaded.findAll { it.key != 'services' }
        assertThat(page.locator('.service h2')).hasText(['ledger', 'notifications', 'notifications-copy', 'mobile-app'] as String[])
        assertThat(page.locator('.generated')).containsText('Pipeline key generated for the new service notifications-copy.')
        assertThat(holdingText(page.locator('.key-value'), store.generatedKeys['notifications-copy'])).isVisible()
        ownErrors().isEmpty()
    }

    def "new services of an existing product get their pipeline keys, shown once after the save"() {
        given:
        def store = ProductStore.recorded(api, 1)
        open('/admin/products/1/edit')

        when:
        button('Add service', true).click()
        input(openService(), 'Service name').fill('worker')
        showSection('Build')
        fillIn(openService(), ['JDK path': '/usr/lib/jvm/java-21-openjdk', 'Gradle tasks': 'clean build'])
        showSection('AppScan SAST and DAST')
        input(openService(), 'AppScan application ID').fill('5b1e9c2d-7a3f-4d6e-8b0a-1c2d3e4f5a6b')
        expandService('gui')
        buttonIn(openService(), 'Duplicate service').click()
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/1')

        then:
        (awaitRequest('PUT', '/api/products/1').json().services as List<Map>).collect { [it.id, it.name] } ==
                [[1, 'gui'], [null, 'gui-copy'], [2, 'backend-api'], [null, 'worker']]
        assertThat(page.locator('.generated')).containsText('Pipeline keys generated for 2 new services: gui-copy, worker.')
        assertThat(page.locator('.generated')).containsText('Copy each key into the Jenkinsfile of its service.')
        store.generatedKeys.keySet() == ['gui-copy', 'worker'] as Set
        store.generatedKeys.values().every { value ->
            assertThat(holdingText(page.locator('.key-value'), value as String)).isVisible()
            true
        }
        assertThat(holdingText(page.locator('.key-value'), '7b62170e…299e')).isVisible()
        assertThat(page.locator('.service .tag.new')).hasCount(2)
        assertThat(holding(page.locator('.page-header .facts > div'), "dt:text-is('Pipelines')").locator('dd')).hasText('5, all active')

        when:
        buttonIn(page.locator('.generated'), 'Dismiss').click()

        then:
        assertThat(page.locator('.generated')).hasCount(0)

        when:
        page.reload()

        then:
        assertThat(page.locator('.service h2')).hasText(['gui', 'gui-copy', 'backend-api', 'worker'] as String[])
        assertThat(page.locator('.generated')).hasCount(0)
        assertThat(holdingText(page.locator('.key-value'), store.generatedKeys.worker as String)).hasCount(0)
        ownErrors().isEmpty()
    }

    def "the Bitbucket repository fields are validated, saved and read back"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def loaded = withoutResponseFields(fixture('product-1.json') as Map)
        open('/admin/products/1/edit')
        expandService('gui')
        showSection('Bitbucket')
        showAdvancedSettings()

        expect:
        hasValues(openService(), ['Repository URL': 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner',
                                  'Credentials ID': 'bitbucket-http-credentials', 'Bitbucket API URL': 'https://bitbucket.bbh.com',
                                  'Workspace'     : '', 'Project key': 'TA', 'Repository slug': 'cert-scanner'])
        assertThat(selected(openService(), 'Sign-in')).hasText('User name and password or token')
        assertThat(selected(openService(), 'Bitbucket')).hasText('Detected from the URL')
        assertThat(hintOf(openService(), 'Workspace')).hasText('Bitbucket Cloud · scm.bitbucket.workspace')

        when:
        fillIn(openService(), ['Workspace': 'bbh technology', 'Bitbucket API URL': 'api.bitbucket.org',
                               'Repository slug': 'team/cert-scanner', 'Repository URL': ''])
        input(openService(), 'Credentials ID').click()

        then:
        hasErrors(openService(), ['Workspace'      : 'No spaces or slashes', 'Repository slug': 'No spaces or slashes',
                                  'Bitbucket API URL': 'An http or https URL without spaces, double quotes, backslashes, \$ or backticks'])

        when:
        button('Save changes', true).click()

        then:
        assertThat(saveError()).hasText('Some fields need your attention.')
        api.requests('PUT', '/api/products/1').isEmpty()

        when:
        fillIn(openService(), ['Workspace'      : 'bbh-technology', 'Bitbucket API URL': 'https://api.bitbucket.org/2.0',
                               'Repository slug': 'cert-scanner-ui', 'Project key': '', 'Target branch': 'develop',
                               'Clone URL'      : 'ssh://git@bitbucket.org/bbh-technology/cert-scanner-ui.git',
                               'Reviewers'      : 'jsmith, akowalski'])
        choose(openService(), 'Sign-in', 'HTTP access token')
        choose(openService(), 'Bitbucket', 'Cloud')
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/1')

        then:
        def scm = [repositoryUrl: null, credentialsId: 'bitbucket-http-credentials', authType: 'BEARER', type: 'CLOUD',
                   targetBranch : 'develop', cloneUrl: 'ssh://git@bitbucket.org/bbh-technology/cert-scanner-ui.git',
                   reviewers    : ['jsmith', 'akowalski'], apiUrl: 'https://api.bitbucket.org/2.0', workspace: 'bbh-technology',
                   projectKey   : null, repoSlug: 'cert-scanner-ui']
        def body = awaitRequest('PUT', '/api/products/1').json() as Map
        body.services[0].scm == scm
        body == loaded + [services: [(loaded.services as List<Map>)[0] + [scm: scm], (loaded.services as List<Map>)[1]]]
        assertThat(page.locator('.service').first().locator('.repository a'))
                .hasAttribute('href', 'https://bitbucket.org/bbh-technology/cert-scanner-ui')
        store.product.version == 1

        when:
        link('Edit product', true).click()
        expandService('gui')
        showSection('Bitbucket')
        showAdvancedSettings()

        then:
        hasValues(openService(), ['Repository URL': '', 'Workspace': 'bbh-technology', 'Project key': '',
                                  'Repository slug': 'cert-scanner-ui', 'Reviewers': 'jsmith, akowalski'])
        assertThat(selected(openService(), 'Sign-in')).hasText('HTTP access token')
        assertThat(selected(openService(), 'Bitbucket')).hasText('Cloud')
        ownErrors().isEmpty()
    }

    def "GoldenFix starts at the global default for new services and each service can follow or override it"() {
        given:
        ProductStore.recorded(api, 2)

        when:
        startProduct('GoldenFix Check')
        showSection('GoldenFix')

        then:
        assertThat(selected(openService(), 'Run GoldenFix')).hasText('Global default')
        assertThat(hintOf(openService(), 'Run GoldenFix')).hasText('Global default: on · goldenFix.enabled')

        when:
        open('/admin/products/2/edit')
        expandService('mobile-app')
        showSection('GoldenFix')

        then:
        assertThat(selected(openService(), 'Run GoldenFix')).hasText('Off')

        when:
        choose(openService(), 'Run GoldenFix', 'Global default')
        expandService('gateway')
        showSection('GoldenFix')
        choose(openService(), 'Run GoldenFix', 'On')
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/2')

        then:
        (awaitRequest('PUT', '/api/products/2').json().services as List<Map>).collect { [it.name, it.goldenFix.enabled] } ==
                [['gateway', true], ['ledger', null], ['notifications', null], ['mobile-app', null]]
        ownErrors().isEmpty()
    }

    def "test job parameters take one NAME=value per line and keep the text as typed"() {
        given:
        ProductStore.recorded(api, 1)
        open('/admin/products/1/edit')
        expandService('gui')
        showSection('Test jobs')
        def regression = holdingText(openService().locator('.list-item'), 'CERT-SCANNER-GUI - regression')

        expect:
        assertThat(input(regression, 'Parameters')).hasValue('ENV=rd')
        input(regression, 'Parameters').evaluate('element => element.tagName') == 'TEXTAREA'
        assertThat(hintOf(regression, 'Parameters')).hasText('One NAME=value per line · parameters')

        when:
        input(regression, 'Parameters').fill('ENV=rd\nSUITE critical')
        input(regression, 'Name').click()

        then:
        assertThat(errorOf(regression, 'Parameters')).hasText('Write each parameter as NAME=value: SUITE critical')

        when:
        button('Save changes', true).click()

        then:
        assertThat(saveError()).hasText('Some fields need your attention.')
        api.requests('PUT', '/api/products/1').isEmpty()

        when:
        input(regression, 'Parameters').fill('ENV=rd\nSUITE=critical\nTAGS=smoke,api')
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/1')

        then:
        def jobs = awaitRequest('PUT', '/api/products/1').json().services[0].testJobs as List<Map>
        jobs*.parameters == [null, null, 'ENV=rd\nSUITE=critical\nTAGS=smoke,api', null]

        when:
        link('Edit product', true).click()
        expandService('gui')
        showSection('Test jobs')

        then:
        assertThat(input(regression, 'Parameters')).hasValue('ENV=rd\nSUITE=critical\nTAGS=smoke,api')
        ownErrors().isEmpty()
    }

    def "a name taken by another service is flagged until either service is renamed"() {
        given:
        ProductStore.recorded(api, 1)
        open('/admin/products/1/edit')

        when:
        expandService('backend-api')
        input(openService(), 'Service name').fill('gui')
        input(openService(), 'Description').click()

        then:
        assertThat(errorOf(openService(), 'Service name')).hasText('another service of this product already uses this name')

        when:
        expandServiceAt(0)
        input(openService(), 'Service name').fill('web')
        expandServiceAt(1)

        then:
        assertThat(errorOf(openService(), 'Service name')).hasCount(0)

        when:
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/1')

        then:
        (awaitRequest('PUT', '/api/products/1').json().services as List<Map>).collect { [it.id, it.name] } == [[1, 'web'], [2, 'gui']]
        ownErrors().isEmpty()
    }

    def "fields hidden by a switch never block the save"() {
        given:
        ProductStore.recorded(api, 1)
        open('/admin/products/1/edit')
        expandService('gui')
        showSection('AppScan SAST and DAST')

        when:
        input(openService(), 'DAST target URL').fill('rdltaapps1.testbbh.com')
        input(openService(), 'DAST scan name').click()

        then:
        assertThat(errorOf(openService(), 'DAST target URL')).hasText('An http or https URL without spaces, double quotes, backslashes, $ or backticks')

        when:
        checkbox(openService(), 'Run DAST against the deployed application').uncheck()
        button('Save changes', true).click()
        page.waitForURL('**/admin/products/1')

        then:
        def appScan = awaitRequest('PUT', '/api/products/1').json().services[0].appScan as Map
        appScan.dastEnabled == false
        appScan.dastTargetUrl == null
        appScan.dastScanName == 'cert-scanner-gui-dast'
        ownErrors().isEmpty()
    }
}
