package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.ProductStore
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.FilePayload
import groovy.json.JsonSlurper

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.LINK

class AddProductSpec extends EditorSpecification {

    static final String GUI_APPLICATION = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b31'
    static final String API_APPLICATION = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b32'
    static final String TAKEN_APPLICATION = '209f44ac-dd06-4ca0-884e-d944904f8020'
    static final String APP_SCAN_KEY = 'bbh_5f0c2a9e-1b7d-4c3e-8a6f-2d9b0e4c7a15'
    static final String ORDERS_APPLICATION = '6f0c2a9e-4b1d-4e57-9a43-0d8e2f6b7c10'

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
        assertThat(errorOf(dialog(), 'Business Service')).hasText('Required')
        assertThat(errorOf(dialog(), 'Product name')).hasText('Required')

        when:
        choose(dialog(), 'Business Service', 'Fund Services')
        input(dialog(), 'Product name').fill('Cert Scanner')
        dialogButton('Continue').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(selected(productFields(), 'Business Service')).hasText('Fund Services')
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
        fillIn(productFields(), ['Name'          : 'CertScanner Next', 'Owner team': 'Technology Architecture',
                                 'Description'   : 'The next generation of the certificate scanner',
                                 'Contact e-mail': 'ta-team@bbh.com', 'Jenkins agents': 'linux-agent, docker'])

        then:
        assertThat(input(productFields(), 'Code')).hasValue('CERT-2')
        assertThat(button('Add service', true)).isEnabled()
        assertThat(page.locator('.blocked')).hasCount(0)

        when:
        fillIn(openService(), ['Service name': 'gui', 'Description': 'Angular front end'])

        then:
        assertThat(button('Add service', true)).isDisabled()
        assertThat(page.locator('.blocked')).hasText('Complete these services before adding another: ' +
                'gui (UrbanCode Deploy, AppScan application ID, AppScan API key ID).')

        when:
        buttonIn(page.locator('.blocked'), 'AppScan API key ID').click()

        then:
        assertThat(openService().locator('.pane-header h3')).hasText('AppScan SAST and DAST')
        assertThat(input(openService(), 'AppScan API key ID')).isFocused()
        assertThat(errorOf(openService(), 'AppScan API key ID')).hasText('Required')

        when:
        showSection('Build')
        fillIn(openService(), ['Artifact path': 'build/libs/*.jar', 'Gradle tasks': 'clean build'])
        showSection('AppScan SAST and DAST')
        fillIn(openService(), ['AppScan application ID'       : GUI_APPLICATION, 'AppScan API key ID': APP_SCAN_KEY,
                               'API key secret credentials ID': 'hcl-app-scan-account'])
        showSection('UrbanCode Deploy')

        then:
        assertThat(openService().locator('.list-error'))
                .hasText('Add an UrbanCode Deploy application for DV: the Full pipeline uses it to deploy to the lower test region')

        when:
        addUrbanCodeApplication('CERT-GUI', 'build/libs')
        button('Add service', true).click()

        then:
        assertThat(serviceNames()).hasText(['gui', 'new-service'] as String[])
        assertThat(openService().locator('.service-name')).hasText('new-service')
        assertThat(button('Add service', true)).isEnabled()

        when:
        input(openService(), 'Service name').fill('backend-api')
        showSection('Build')
        choose(openService(), 'Build tool', 'Maven')
        fillIn(openService(), ['Artifact path': 'target/*.jar', 'Maven goals': 'clean verify'])
        showSection('Deployment')
        input(openService(), 'Maven goals').fill('deploy')
        addUrbanCodeApplication('CERT-API', 'target')
        showSection('AppScan SAST and DAST')
        input(openService(), 'AppScan application ID').fill(TAKEN_APPLICATION)
        button('Add product', true).click()

        then:
        assertThat(saveError()).hasText('The portal did not accept some values. They are marked in the form.')
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

    def "a new product fills its blank service from a config.yaml"() {
        given:
        def ledger = (fixture('product-2.json') as Map).services.find { it.name == 'ledger' }
        api.respond('POST', '/api/service-configs/import', [
                services: [[name: 'ledger-api', service: ledger, notes: []]],
                pipeline: [type: 'FULL', agentLabels: ['docker-agent']],
                notes   : []])
        startProduct('Ledger Next')

        expect:
        assertThat(serviceNames()).hasText(['new-service'] as String[])
        assertThat(button('Import devsecops_config.yaml', true)).isEnabled()
        assertThat(page.locator('.blocked')).hasCount(0)

        when:
        page.locator('.service-actions input[type=file]')
                .setInputFiles(new FilePayload('config.yaml', 'application/yaml', 'projects: {}'.getBytes('UTF-8')))

        then:
        assertThat(serviceNames()).hasText(['ledger-api'] as String[])
        assertThat(openService().locator('.tag.new')).hasText('New')
        assertThat(input(productFields(), 'Jenkins agents')).hasValue('docker-agent')
        assertThat(page.locator('dso-import-notes')).containsText('ledger-api was imported from config.yaml.')
        api.requests('POST', '/api/service-configs/import').size() == 1
        ownErrors().isEmpty()
    }

    def "a new product imports devsecops_config.yaml from two chosen files and says that config.yaml was not read"() {
        given:
        def ledger = (fixture('product-2.json') as Map).services.find { it.name == 'ledger' }
        api.respond('POST', '/api/service-configs/import', [
                services: [[name: 'ledger-api', service: ledger, notes: []]],
                pipeline: [type: 'FULL', agentLabels: ['docker-agent']],
                notes   : []])
        startProduct('Ledger Next')

        when:
        page.locator('.service-actions input[type=file]').setInputFiles([
                new FilePayload('config.yaml', 'application/yaml', 'projects: {old: {}}'.getBytes('UTF-8')),
                new FilePayload('devsecops_config.yaml', 'application/yaml', 'projects: {}'.getBytes('UTF-8'))
        ] as FilePayload[])

        then:
        assertThat(serviceNames()).hasText(['ledger-api'] as String[])
        assertThat(page.locator('dso-import-notes')).containsText('ledger-api was imported from devsecops_config.yaml.')
        assertThat(page.locator('dso-import-notes')).containsText(
                'The import did not read config.yaml: the DevSecOps library reads devsecops_config.yaml when a repository has both')
        api.requests('POST', '/api/service-configs/import')*.json()*.content == ['projects: {}']
        ownErrors().isEmpty()
    }

    def "a product with a SQL and PL/SQL service typed in the editor sends its changelog and its databases"() {
        given:
        allowDatabases()
        ProductStore.created(api, 3)
        startProduct('Orders Database')

        when:
        fillIn(productFields(), ['Code'          : 'ORDERSDB', 'Owner team': 'Order Management',
                                 'Description'   : 'The order book of the custody clients',
                                 'Contact e-mail': 'orders-team@bbh.com'])
        fillIn(openService(), ['Service name': 'orders-db', 'Description': 'SQL and PL/SQL of the orders schema'])
        showSection('Build')
        choose(openService(), 'Build tool', 'SQL and PL/SQL (Liquibase)')
        checkbox(openService(), 'Set up the build tool automatically').uncheck()
        input(openService(), 'JDK path').fill('/usr/lib/jvm/java-17-openjdk')

        then:
        assertThat(sectionNames()).hasText(['General', 'Build', 'Database changes', 'Test jobs', 'AppScan SAST and DAST',
                                            'SonarQube', 'Bitbucket', 'Monitoring'] as String[])

        when:
        showSection('Database changes')
        fillIn(openService().locator('.pane > .form-fields').first(), ['Changelog file'      : 'db/changelog-master.xml',
                                                                     'Changelog parameters': 'app_role=orders_app_role'])
        input(openService(), 'Group ID').fill('com.bbh.orders')
        fillIn(databaseBlock('test'), ['JDBC URL': 'jdbc:oracle:thin:@//dev-db.bbh.com:1521/ORDERS_CI', 'Credentials ID': 'orders-db-test'])
        checkbox(databaseBlock('test'), 'Run the utPLSQL tests').check()
        fillIn(databaseBlock('test'), ['Folder of the packages': 'db/code', 'Folder of the test packages': 'db/test'])
        fillIn(databaseBlock('rd'), ['JDBC URL': 'jdbc:oracle:thin:@//rd-db.bbh.com:1521/ORDERS_RD', 'Credentials ID': 'orders-db-rd'])
        fillIn(databaseBlock('qc'), ['JDBC URL'      : 'jdbc:oracle:thin:@tcps://qc-db.bbh.com:2484/ORDERS_QC?ssl_server_dn_match=true',
                                     'Credentials ID': 'orders-db-qc'])
        showAdvancedSettings()
        input(openService().locator("dso-advanced-settings h4:has(> code:text-is('database.qc')) + dso-fields"),
                'Changelog parameters').fill('app_role=orders_qc_role')
        showSection('AppScan SAST and DAST')
        fillIn(openService(), ['AppScan application ID'       : ORDERS_APPLICATION, 'AppScan API key ID': "bbh_$ORDERS_APPLICATION".toString(),
                               'API key secret credentials ID': 'appscan-orders-key-secret'])
        showSection('SonarQube')
        input(openService(), 'Project key').fill('bbh-orders-db')
        showSection('Bitbucket')
        input(openService(), 'Repository URL').fill('https://bitbucket.bbh.com/projects/ORD/repos/orders')
        button('Add product', true).click()
        page.waitForURL('**/admin/products/3')

        then:
        api.lastRequest('POST', '/api/products').json() == expected('new-database-product-request.json')
        assertThat(page.locator('h1')).hasText('Orders Database')
        ownErrors().isEmpty()
    }

    static Object expected(String name) {
        new JsonSlurper().parse(AddProductSpec.getResource(name))
    }
}
