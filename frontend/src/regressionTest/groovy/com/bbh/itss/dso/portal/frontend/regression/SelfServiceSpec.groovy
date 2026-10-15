package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.ProductStore
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
import static com.microsoft.playwright.options.AriaRole.RADIOGROUP

class SelfServiceSpec extends EditorSpecification {

    static final String API_APPLICATION = '4b1c2d3e-1111-4a5b-8c9d-0e1f2a3b4c5d'
    static final String GUI_APPLICATION = '4b1c2d3e-2222-4a5b-8c9d-0e1f2a3b4c5d'
    static final String TAKEN_APPLICATION = '209f44ac-dd06-4ca0-884e-d944904f8020'
    static final String APP_SCAN_KEY = 'bbh_9a1b2c3d-0000-4abc-9def-123456789abc'
    static final String SAST_KEY_OF_GUI = '2c0ca4f4-a1a6-472a-9685-0c75f22fe713'
    static final String SECURITY_KEY_OF_GATEWAY = '5a07b656-ed5b-46b6-b3af-eb043ca15627'
    static final String GUI_REPOSITORY = 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner-gui'
    static final String SCANNER_REPOSITORY = 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner-batch'
    static final String SAST = 'SAST (Static Application Security Tests) - HCL AppScan'
    static final String OSA = 'OSA (Open Source Analysis) (NexusIQ with Golden Fix and Golden Pull Request)'

    def "a product manager adds a new product with a Security pipeline step by step"() {
        given:
        recordClipboard()
        useSourceFolder('app')
        api.respond('POST', '/api/products', problem(400, 'Bad Request', 'The portal did not accept some values.',
                [errors: [[field: 'services[1].appScan.applicationId', message: 'the AppScan application belongs to CertScanner']]]))

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
        assertThat(step().locator('.fields dso-label').first()).hasText('Department')
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
        assertThat(step().locator('.tile-description')).hasText(['Unit Tests, NexusIQ, SAST, SonarQube',
                                                                 'Static Security (unit test, NexusIQ, SAST, SonarQube) + Extended (lower test region deployment, regression, performance, smoke, DAST, *higher test region deployment)'] as String[])
        assertThat(step().locator('.tile-note')).hasCount(0)
        assertThat(step().locator('.today')).hasCount(0)
        assertThat(choiceError()).hasText('Choose a pipeline to continue')

        when:
        radio(step(), 'Security').click()

        then:
        assertThat(radio(step(), 'Security')).hasAttribute('aria-checked', 'true')
        assertThat(step().locator('.prepare li')).hasCount(3)

        when:
        button('Next: Services', true).click()
        button('Next: Review', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Which services does Trade Archive have?')
        assertThat(choiceError()).hasText('Add at least one service')

        when:
        button('Add a service', true).click()
        fillIn(dialog(), ['Service name': 'archive-api', 'What it does': 'REST API of the archive',
                          'AppScan application ID': API_APPLICATION])
        dialogButton('Next: Build and run').click()

        then:
        assertThat(dialog().locator('.page-count')).hasText('Part 2 of 2 · Build and run')
        assertThat(radio(dialog(), 'Gradle')).hasAttribute('aria-checked', 'true')
        assertThat(radio(dialog(), 'Virtual machines')).hasAttribute('aria-checked', 'true')
        assertThat(dialog().locator('.choice-error')).hasCount(0)

        when:
        radio(dialog(), 'Maven').click()
        radio(dialog(), 'OpenShift').click()

        then:
        assertThat(input(dialog(), 'OpenShift project')).hasValue('tradearchive-archive-api')

        when:
        input(dialog(), 'OpenShift project').fill('cus-archive')
        dialogButton('Add service').click()
        addService('archive-gui', TAKEN_APPLICATION, 'Gradle', 'Virtual machines')

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(step().locator('.service-list strong')).hasText(['archive-api', 'archive-gui'] as String[])
        assertThat(step().locator('.service-list .muted')).hasText(['Built with Maven, runs on OpenShift in project cus-archive.',
                                                                    'Built with Gradle, runs on virtual machines.'] as String[])
        assertThat(serviceRow('archive-api')).containsText('REST API of the archive')

        when:
        button('Next: Review', true).click()
        button('Create the pipelines', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(review('Department')).hasText('Custody')
        assertThat(review('Pipeline')).hasText('Security')
        hasEntries(['archive-api': 'New · Added with a Security pipeline and its own key. Built with Maven, runs on OpenShift in project cus-archive.',
                    'archive-gui': 'New · Added with a Security pipeline and its own key. Built with Gradle, runs on virtual machines.'])
        assertThat(page.locator('.save-problem strong')).hasText('The portal did not accept some values.')
        assertThat(page.locator('.save-problem .problems li'))
                .hasText(['archive-gui, AppScan application ID: the AppScan application belongs to CertScanner'] as String[])

        when:
        def store = ProductStore.created(api, 3)
        page.locator('.step-bar').getByRole(BUTTON).filter(new Locator.FilterOptions().setHasText('Services')).click()
        button('Change archive-gui', true).click()
        input(dialog(), 'AppScan application ID').fill(GUI_APPLICATION)
        dialogButton('Next: Build and run').click()
        dialogButton('Save service').click()
        button('Next: Review', true).click()
        button('Create the pipelines', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Trade Archive is saved. Now do these steps in order')
        def request = awaitRequest('POST', '/api/products', 2)
        request.params() == [pipelineType: 'SECURITY']
        with(request.json() as Map) {
            code == 'TRADEARCHIVE'
            name == 'Trade Archive'
            departmentId == 4
            ownerTeam == 'Custody Technology'
            contactEmail == null
            appScan.keyId == SelfServiceSpec.APP_SCAN_KEY
            services*.name == ['archive-api', 'archive-gui']
            services*.appScan*.applicationId == [SelfServiceSpec.API_APPLICATION, SelfServiceSpec.GUI_APPLICATION]
            services*.build*.tool == ['MAVEN', 'GRADLE']
            services*.build*.sourceDir == ['app', 'app']
            services*.deployment*.target == ['OPENSHIFT', 'VM']
            services[0].openShiftTargets.RD.projectDeployment == 'cus-archive-rd'
            services[0].openShiftTargets.QC.projectDeployment == 'cus-archive-qc'
            services[1].openShiftTargets == [:]
        }
        store.services*.pipelines*.type == [['SECURITY'], ['SECURITY']]
        assertThat(page.locator('.jenkinsfile .code-block')).hasText(store.services.collect { service ->
            "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys[service.serviceName]}')".toString()
        } as String[])
        assertThat(page.locator('.next-steps > li h3')).hasText(["Put the Jenkinsfile in each service's repository",
                                                                'Ask your Jenkins administrator for a job for each service',
                                                                'Run each job once', 'Follow the results'] as String[])
        assertThat(page.locator('.job-names li')).hasText(['archive-api: DevSecOps/TRADEARCHIVE/archive-api-security',
                                                           'archive-gui: DevSecOps/TRADEARCHIVE/archive-gui-security'] as String[])
        assertThat(link('Open Trade Archive in DevSecOps Admin', true)).hasAttribute('href', '/admin/products/3')

        when:
        button('Copy the Jenkinsfile of archive-api', true).click()

        then:
        assertThat(snackBar()).containsText('Jenkinsfile copied to the clipboard')
        copiedTexts() == ["@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys['archive-api']}')\n".toString()]
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "a product in the portal gets a SAST pipeline next to the pipelines it has, a new service included"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def stored = fixture('product-1.json') as Map

        when:
        open('/self-service')
        radio(step(), 'A product in the portal').click()
        button('Next: Pipeline', true).click()

        then:
        assertThat(errorOf(step(), 'Department')).hasText('Required')
        assertThat(choiceError()).hasText('Choose the product')

        when:
        choose(step(), 'Department', 'Corporate Technology')

        then:
        assertThat(productGroups()).hasCount(1)
        assertThat(productGroups()).hasAttribute('label', 'Corporate Technology')

        when:
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')

        then:
        assertThat(hintOf(step(), 'Product')).hasText('1 product to choose from')
        assertThat(step().locator('dl.rows dd')).hasText(['Technology Architecture', '2'] as String[])

        when:
        button('Next: Pipeline', true).click()

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('.lead'))
                .containsText('the one you choose is added to every service that does not have it yet, and their other pipelines stay as they are.')
        assertThat(step().locator('.today li')).hasText(['gui · Full, SAST', 'backend-api · Full'] as String[])
        assertThat(step().locator('.tile-note'))
                .hasText(['1 of 2 services has it', 'No service has it yet', 'No service has it yet', 'Every service has it'] as String[])

        when:
        radio(step(), SAST).click()
        button('Next: Services', true).click()

        then:
        assertThat(step().locator('.service-list .muted').first()).hasText('Built with Gradle, runs on virtual machines.')
        assertThat(button('Remove')).hasCount(2)

        when:
        buttonIn(serviceRow('gui'), 'Change gui').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Change gui')
        assertThat(dialog().locator('.page-count')).hasText('Part 1 of 2 · About the service')

        when:
        input(dialog(), 'What it does').fill('Web front end')
        dialogButton('Next: Build and run').click()

        then:
        assertThat(dialog().getByRole(RADIOGROUP)).hasCount(1)
        assertThat(dialog().locator('.note')).hasText('If you change how it is built, its build settings go back to the BBH defaults.')

        when:
        dialogButton('Save service').click()
        addService('scanner', API_APPLICATION, 'Maven')

        then:
        assertThat(step().locator('.service-list strong')).hasText(['gui', 'backend-api', 'scanner'] as String[])
        assertThat(step().locator('.service-list .tag')).hasText(['Changed', 'New'] as String[])
        assertThat(button('Remove')).hasCount(3)

        when:
        button('Next: Review', true).click()

        then:
        assertThat(step().locator('.lead'))
                .hasText('Nothing is saved until you press Save the changes; use Back or the numbered steps above to change an answer.')
        hasEntries(['gui'        : 'Changed · Keeps its SAST pipeline. New description.',
                    'backend-api': 'Gets a SAST pipeline with its own key; nothing else changes.',
                    'scanner'    : 'New · Added with a SAST pipeline and its own key. Built with Maven.'])
        assertThat(step().locator('.removal-warning')).hasCount(0)

        when:
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
            services[0].build == stored.services[0].build
            services[1].build == stored.services[1].build
            services[1].deployment == stored.services[1].deployment
            services[2].build.tool == 'MAVEN'
            services[2].deployment.target == 'VM'
        }
        store.services*.pipelines*.type == [['FULL', 'SAST'], ['FULL', 'SAST'], ['SAST']]
        store.generatedKeys.keySet() == ['backend-api', 'scanner'] as Set
        assertThat(page.locator('.jenkinsfile .code-block')).containsText([SAST_KEY_OF_GUI, store.generatedKeys['backend-api'],
                                                                    store.generatedKeys['scanner']] as String[])
        assertThat(page.locator('.next-steps > li')).hasCount(4)
        assertThat(page.locator('.job-names code')).hasText(['DevSecOps/CERTSCANNER/gui-sast', 'DevSecOps/CERTSCANNER/backend-api-sast',
                                                             'DevSecOps/CERTSCANNER/scanner-sast'] as String[])

        when:
        button('Set up another product', true).click()

        then:
        assertThat(currentStep()).hasText('Product')
        assertThat(radio(step(), 'A new product')).hasAttribute('aria-checked', 'true')
        assertThat(input(step(), 'Product name')).hasValue('')
        ownErrors().isEmpty()
    }

    def "a product in the portal gets an OSA pipeline that raises golden pull requests in Bitbucket"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def stored = fixture('product-1.json') as Map

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), OSA).click()

        then:
        assertThat(radio(step(), OSA)).hasAttribute('aria-checked', 'true')
        assertThat(step().locator('.prepare li').last())
                .hasText('The Nexus IQ application and the Bitbucket repository of each service')

        when:
        button('Next: Services', true).click()

        then:
        assertThat(step().locator('.lead')).containsText('each one gets its own OSA pipeline.')
        assertThat(serviceRow('gui').locator('.muted').first())
                .hasText('Built with Gradle, runs on virtual machines, Nexus IQ application cert-scanner-gui.')

        when:
        buttonIn(serviceRow('gui'), 'Change gui').click()
        dialogButton('Next: Build and run').click()

        then:
        assertThat(dialog().getByRole(RADIOGROUP)).hasCount(1)
        hasValues(dialog(), ['Nexus IQ application': 'cert-scanner-gui',
                             'Bitbucket repository': stored.services[0].scm.repositoryUrl as String])

        when:
        fillIn(dialog(), ['Nexus IQ application': '', 'Bitbucket repository': 'bitbucket.bbh.com/projects/TA/repos/cert-scanner-gui'])
        dialogButton('Save service').click()

        then:
        hasErrors(dialog(), ['Nexus IQ application': 'Required',
                             'Bitbucket repository': 'An http or https URL without spaces, double quotes, backslashes, $ or backticks'])

        when:
        fillIn(dialog(), ['Nexus IQ application': 'cert-scanner-web', 'Bitbucket repository': GUI_REPOSITORY])
        dialogButton('Save service').click()
        addService('scanner', API_APPLICATION, 'Maven', null,
                ['Nexus IQ application': 'cert-scanner-batch', 'Bitbucket repository': SCANNER_REPOSITORY])

        then:
        assertThat(step().locator('.service-list strong')).hasText(['gui', 'backend-api', 'scanner'] as String[])
        assertThat(step().locator('.service-list .tag')).hasText(['Changed', 'New'] as String[])

        when:
        button('Next: Review', true).click()

        then:
        assertThat(review('Pipeline')).hasText('OSA')
        hasEntries(['gui'        : 'Changed · Gets an OSA pipeline with its own key. New Nexus IQ application; new Bitbucket repository.',
                    'backend-api': 'Gets an OSA pipeline with its own key; nothing else changes.',
                    'scanner'    : 'New · Added with an OSA pipeline and its own key. Built with Maven, Nexus IQ application cert-scanner-batch.'])

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is saved. Now do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'NEXUS_IQ']
        with(request.json() as Map) {
            services*.id == [1, 2, null]
            services[0].nexusIqApplications == [stored.services[0].nexusIqApplications[0] + [application: 'cert-scanner-web']]
            services[0].scm == stored.services[0].scm + [repositoryUrl: SelfServiceSpec.GUI_REPOSITORY]
            services[0].build == stored.services[0].build
            services[0].deployment == stored.services[0].deployment
            services[1].nexusIqApplications == stored.services[1].nexusIqApplications
            services[1].scm == stored.services[1].scm
            services[2].nexusIqApplications == [[application: 'cert-scanner-batch', scanPatterns: ['**/target/*.jar'],
                                                 stage      : 'build', failOnNetworkError: false]]
            services[2].scm.repositoryUrl == SelfServiceSpec.SCANNER_REPOSITORY
            services[2].scm.credentialsId == 'bitbucket-http-credentials'
            services[2].build.tool == 'MAVEN'
            services[2].deployment.target == 'VM'
            services[2].appScan.applicationId == SelfServiceSpec.API_APPLICATION
        }
        store.services*.pipelines*.type == [['FULL', 'SAST', 'NEXUS_IQ'], ['FULL', 'NEXUS_IQ'], ['NEXUS_IQ']]
        store.product.services*.nexusIqApplications*.application == [['cert-scanner-web'], ['cert-scanner-backend'], ['cert-scanner-batch']]
        store.product.services*.scm*.repositoryUrl == [GUI_REPOSITORY, stored.services[1].scm.repositoryUrl, SCANNER_REPOSITORY]
        store.generatedKeys.keySet() == ['gui', 'backend-api', 'scanner'] as Set
        assertThat(page.locator('.jenkinsfile .code-block')).hasText(store.services.collect { service ->
            "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsNexusIqGoldenFixPipeline(pipelineKey: '${store.generatedKeys[service.serviceName]}')".toString()
        } as String[])
        assertThat(page.locator('.next-steps > li h3')).hasText(["Put the Jenkinsfile in each service's repository",
                                                                'Ask your Jenkins administrator for a job for each service',
                                                                'Run each job once', 'Review the golden pull requests',
                                                                'Follow the results'] as String[])
        assertThat(page.locator('.jenkinsfile-head .muted')).hasText(store.product.services.collect { service ->
            "· goes into ${service.scm.repositoryUrl}".toString()
        } as String[])
        assertThat(page.locator('.job-names code').first()).hasText('DevSecOps/CERTSCANNER/gui-nexusiq')
        ownErrors().isEmpty()
    }

    def "a product in the portal without a department is put into one on the way"() {
        given:
        def store = ProductStore.recorded(api, 2)
        store.product.departmentId = null
        def products = fixture('products.json') as List<Map>
        api.respond('GET', '/api/products', [products[0], products[1] + [departmentId: null, departmentName: null]])

        when:
        open('/self-service')
        radio(step(), 'A product in the portal').click()

        then:
        assertThat(productGroups()).hasCount(1)
        assertThat(productGroups()).hasAttribute('label', 'Not in a department')

        when:
        choose(step(), 'Product', 'Payments Hub (PAYHUB)')
        assertThat(step().locator('dl.rows dt')).hasText(['Owner team', 'Services'] as String[])
        button('Next: Pipeline', true).click()

        then:
        assertThat(currentStep()).hasText('Product')
        assertThat(errorOf(step(), 'Department')).hasText('Required')

        when:
        choose(step(), 'Department', 'Fund Services')

        then:
        assertThat(hintOf(step(), 'Department')).hasText('Saving moves the product into this department')
        assertThat(select(step(), 'Product').locator('option:checked')).hasText('Payments Hub (PAYHUB)')

        when:
        button('Next: Pipeline', true).click()
        radio(step(), SAST).click()
        button('Next: Services', true).click()
        button('Next: Review', true).click()

        then:
        assertThat(review('Department')).hasText('Fund Services')

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Payments Hub is saved. Now do these steps in order')
        awaitRequest('PUT', '/api/products/2').json().departmentId == 5
        store.product.departmentId == 5
        ownErrors().isEmpty()
    }

    def "changing the build tool of a service in the portal puts its build settings back to the BBH defaults"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def stored = fixture('product-1.json') as Map

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), 'Full').click()
        button('Next: Services', true).click()
        buttonIn(serviceRow('gui'), 'Change gui').click()
        dialogButton('Next: Build and run').click()

        then:
        assertThat(radio(dialog(), 'Gradle')).hasAttribute('aria-checked', 'true')
        assertThat(radio(dialog(), 'Virtual machines')).hasAttribute('aria-checked', 'true')
        assertThat(dialog().locator('.note'))
                .hasText('If you change how it is built or where it runs, its build or deployment settings go back to the BBH defaults.')

        when:
        radio(dialog(), 'Maven').click()
        dialogButton('Save service').click()

        then:
        assertThat(serviceRow('gui').locator('.tag')).hasText('Changed')
        assertThat(serviceRow('gui').locator('.muted').first()).hasText('Built with Maven, runs on virtual machines.')

        when:
        button('Next: Review', true).click()

        then:
        hasEntries(['gui'        : 'Changed · Keeps its Full pipeline. Built with Maven instead of Gradle, with the default build settings.',
                    'backend-api': 'Already has a Full pipeline; nothing changes.'])

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is saved. Now do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'FULL']
        with(request.json() as Map) {
            services*.id == [1, 2]
            services[0].build.tool == 'MAVEN'
            services[0].build.autoSetup
            services[0].build.javaPath == null
            services[0].build.buildPath == 'target/*.jar'
            services[0].build.command.tasks == ['clean', 'verify']
            services[0].delivery.tasks == ['deploy:deploy-file']
            services[0].deployment == stored.services[0].deployment
            services[0].testJobs*.job == stored.services[0].testJobs*.job
            services[0].appScan.applicationId == stored.services[0].appScan.applicationId
            services[1].build == stored.services[1].build
        }
        store.services*.pipelines*.type == [['FULL', 'SAST'], ['FULL']]
        store.generatedKeys.isEmpty()
        ownErrors().isEmpty()
    }

    def "removing a service of the portal deletes it with its pipelines and keys on save"() {
        given:
        def store = ProductStore.recorded(api, 1)

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), 'Security').click()
        button('Next: Services', true).click()
        buttonIn(serviceRow('backend-api'), 'Remove backend-api').click()

        then:
        assertThat(serviceRow('backend-api').locator('.tag')).hasText('Removed')
        assertThat(serviceRow('backend-api').locator('.removal')).hasText(
                'Saving deletes backend-api, its pipelines (Full) and their keys. Jenkins jobs that use these keys stop working.')
        assertThat(buttonIn(serviceRow('backend-api'), 'Change backend-api')).hasCount(0)

        when:
        buttonIn(serviceRow('gui'), 'Remove gui').click()
        button('Next: Review', true).click()

        then:
        assertThat(currentStep()).hasText('Services')
        assertThat(choiceError()).hasText('Keep at least one service')

        when:
        buttonIn(serviceRow('gui'), 'Undo removing gui').click()
        button('Next: Review', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        hasEntries(['gui'        : 'Gets a Security pipeline with its own key; nothing else changes.',
                    'backend-api': 'Removed · Deleted, with its pipelines and their keys.'])
        assertThat(step().locator('.removal-warning strong'))
                .hasText('Saving deletes these pipelines and their keys. Jenkins jobs that use these keys stop working.')
        assertThat(step().locator('.removal-warning li')).hasText(['backend-api · Full'] as String[])

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is saved. Now do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'SECURITY']
        (request.json() as Map).services*.name == ['gui']
        store.services*.serviceName == ['gui']
        store.services*.pipelines*.type == [['FULL', 'SAST', 'SECURITY']]
        assertThat(page.locator('.jenkinsfile strong')).hasText(['gui'] as String[])
        ownErrors().isEmpty()
    }

    def "adding a Security pipeline shows the pipelines the services of a product already have"() {
        given:
        def store = ProductStore.recorded(api, 2)

        when:
        openProduct('Fund Services', 'Payments Hub (PAYHUB)')

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('h2')).hasText('Which pipeline does Payments Hub need?')
        assertThat(step().locator('.today li')).hasText(['gateway · Full, Security, Extended', 'ledger · Full',
                                                         'notifications · Full', 'mobile-app · SAST'] as String[])
        assertThat(step().locator('.tile-note'))
                .hasText(['1 of 4 services has it', 'No service has it yet', '1 of 4 services has it', '3 of 4 services have it'] as String[])

        when:
        radio(step(), 'Security').click()
        button('Next: Services', true).click()

        then:
        assertThat(step().locator('.service-list strong'))
                .hasText(['gateway', 'ledger', 'notifications', 'mobile-app'] as String[])
        assertThat(serviceRow('mobile-app').locator('.muted').first()).hasText('Built with Flutter, runs on virtual machines.')

        when:
        button('Next: Review', true).click()

        then:
        assertThat(step().locator('dl.rows dt')).hasText(['Product', 'Department', 'Pipeline'] as String[])
        assertThat(review('Pipeline')).hasText('Security')
        hasEntries(['gateway'      : 'Already has a Security pipeline; nothing changes.',
                    'ledger'       : 'Gets a Security pipeline with its own key; nothing else changes.',
                    'notifications': 'Gets a Security pipeline with its own key; nothing else changes.',
                    'mobile-app'   : 'Gets a Security pipeline with its own key; nothing else changes.'])

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Payments Hub is saved. Now do these steps in order')
        def request = awaitRequest('PUT', '/api/products/2')
        request.params() == [pipelineType: 'SECURITY']
        (request.json() as Map).services*.id == [3, 4, 5, 6]
        store.services*.pipelines*.type == [['EXTENDED', 'FULL', 'SECURITY'], ['FULL', 'SECURITY'],
                                            ['FULL', 'SECURITY'], ['SAST', 'SECURITY']]
        store.generatedKeys.keySet() == ['ledger', 'notifications', 'mobile-app'] as Set
        assertThat(page.locator('.jenkinsfile .code-block')).containsText([SECURITY_KEY_OF_GATEWAY, store.generatedKeys['ledger'],
                                                                    store.generatedKeys['notifications'],
                                                                    store.generatedKeys['mobile-app']] as String[])
        assertThat(page.locator('.job-names code')).hasText(['DevSecOps/PAYHUB/gateway-security', 'DevSecOps/PAYHUB/ledger-security',
                                                             'DevSecOps/PAYHUB/notifications-security',
                                                             'DevSecOps/PAYHUB/mobile-app-security'] as String[])
        ownErrors().isEmpty()
    }

    def "a service on OpenShift that gets another build tool builds its image from the folder of that tool"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def stored = fixture('product-1.json') as Map
        def targets = stored.services[1].openShiftTargets as Map
        useSourceFolder('app')

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), 'Full').click()
        button('Next: Services', true).click()
        buttonIn(serviceRow('backend-api'), 'Change backend-api').click()
        dialogButton('Next: Build and run').click()
        radio(dialog(), 'Gradle').click()
        dialogButton('Save service').click()
        button('Next: Review', true).click()

        then:
        hasEntries(['gui'        : 'Already has a Full pipeline; nothing changes.',
                    'backend-api': 'Changed · Keeps its Full pipeline. Built with Gradle instead of Maven, with the default build settings.'])

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is saved. Now do these steps in order')
        with(awaitRequest('PUT', '/api/products/1').json() as Map) {
            services[0].build == stored.services[0].build
            services[1].build.tool == 'GRADLE'
            services[1].build.sourceDir == 'app'
            services[1].build.buildPath == 'build/libs/*.jar'
            services[1].deployment == stored.services[1].deployment
            services[1].openShiftTargets == targets + [RD: targets.RD + [buildContext: 'build/docker']]
        }
        store.generatedKeys.isEmpty()
        ownErrors().isEmpty()
    }

    def "a remembered department that is no longer listed has to be chosen again"() {
        given:
        page.addInitScript("localStorage.setItem('dso.beadle.department', '99')")

        when:
        open('/self-service')
        button('Next: Pipeline', true).click()

        then:
        assertThat(currentStep()).hasText('Product')
        assertThat(select(step(), 'Department')).hasValue('')
        assertThat(errorOf(step(), 'Department')).hasText('Required')
        ownErrors().isEmpty()
    }

    def "new services wait for the service template, which can be loaded again"() {
        given:
        ProductStore.recorded(api, 1)
        api.respond('GET', '/api/service-template', problem(503, 'Service Unavailable', 'The database is not reachable'))

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), SAST).click()
        button('Next: Services', true).click()
        button('Next: Review', true).click()

        then:
        assertThat(currentStep()).hasText('Review')

        when:
        button('Back', true).click()
        addService('scanner', API_APPLICATION, 'Maven')
        button('Next: Review', true).click()

        then:
        assertThat(currentStep()).hasText('Services')
        assertThat(choiceError()).hasText(['The service template could not be loaded. The database is not reachable. New services, and services that change how they are built or where they run, get their build settings from it.',
                                           'These services get their build settings from the service template, which has to load first: scanner'] as String[])

        when:
        api.respond('GET', '/api/service-template', fixture('service-template.json'))
        button('Try again', true).click()

        then:
        assertThat(button('Try again', true)).hasCount(0)

        when:
        button('Next: Review', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    def "a service whose key is invalidated is sent to its pipeline page for a new key"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def sast = store.services[0].pipelines.find { it.type == 'SAST' } as Map
        sast.activeKey = null
        sast.enabled = false

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), SAST).click()
        button('Next: Services', true).click()
        button('Next: Review', true).click()
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is saved. Now do these steps in order')
        assertThat(page.locator('.jenkinsfile strong')).hasText(['gui', 'backend-api'] as String[])
        assertThat(button('Copy the Jenkinsfile of gui', true)).hasCount(0)
        assertThat(page.locator('.jenkinsfile .code-block')).hasText(
                ["@Library('DevSecOpsJenkinsLibrary') _ devSecOpsSASTScanningPipeline(pipelineKey: '${store.generatedKeys['backend-api']}')".toString()] as String[])
        assertThat(page.locator('.jenkinsfile-missing')).hasText(
                'Its key is invalidated, so the pipeline is refused its settings and stops. Issue a new key on its pipeline page and copy the Jenkinsfile from there.')

        when:
        link('its pipeline page', true).click()

        then:
        page.waitForURL("**/pipelines/${sast.id}")
        ownErrors().isEmpty()
    }

    def "a new service that has the name of a service of the product chosen after it has to be renamed"() {
        given:
        ProductStore.recorded(api, 1)
        ProductStore.recorded(api, 2)

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), SAST).click()
        button('Next: Services', true).click()
        addService('ledger', API_APPLICATION, 'Gradle')
        page.locator('.step-bar').getByRole(BUTTON).filter(new Locator.FilterOptions().setHasText('Product')).click()
        choose(step(), 'Department', 'Fund Services')
        choose(step(), 'Product', 'Payments Hub (PAYHUB)')
        button('Next: Pipeline', true).click()
        button('Next: Services', true).click()
        button('Next: Review', true).click()

        then:
        assertThat(currentStep()).hasText('Services')
        assertThat(step().locator('.service-list strong'))
                .hasText(['gateway', 'ledger', 'notifications', 'mobile-app', 'ledger'] as String[])
        assertThat(choiceError())
                .hasText('Use Change to rename these services, as another service of Payments Hub has the same name: ledger')
        ownErrors().isEmpty()
    }

    def "leaving the wizard with answers asks before they are lost"() {
        when:
        open('/self-service')
        input(step(), 'Product name').fill('Trade Archive')
        menuLink('Pipeline Monitoring').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(currentStep()).hasText('Product')
        assertThat(input(step(), 'Product name')).hasValue('Trade Archive')

        when:
        menuLink('Pipeline Monitoring').click()
        dialogButton('Discard').click()
        page.waitForURL('**/monitoring')

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Pipeline Monitoring')
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

    void useSourceFolder(String folder) {
        def settings = fixture('settings.json') as Map
        settings.serviceDefaults.sourceDir = folder
        api.respond('GET', '/api/settings', settings)
    }

    void addService(String name, String applicationId, String tool, String target = null, Map<String, String> details = [:]) {
        button('Add a service', true).click()
        fillIn(dialog(), ['Service name': name, 'AppScan application ID': applicationId])
        dialogButton('Next: Build and run').click()
        radio(dialog(), tool).click()
        if (target) {
            radio(dialog(), target).click()
        }
        fillIn(dialog(), details)
        dialogButton('Add service').click()
    }

    @Override
    Locator hintOf(Locator scope, String label) {
        formField(scope, label).locator('dso-hint')
    }

    Locator step() {
        page.locator('section.step')
    }

    Locator productGroups() {
        select(step(), 'Product').locator('optgroup')
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

    void hasEntries(Map<String, String> entries) {
        assertThat(step().locator('.review-list strong')).hasText(entries.keySet() as String[])
        entries.each { name, entry ->
            def (tag, text) = entry.contains(' · ') ? entry.split(' · ') as List : ['', entry]
            def row = holding(step().locator('.review-list li'), "strong:text-is('${name}')")
            assertThat(row.locator('.review-name')).hasText(name + tag)
            assertThat(row.locator('.review-text')).hasText(text)
        }
    }

    Locator serviceRow(String name) {
        holding(step().locator('.service-list li'), "strong:text-is('${name}')")
    }
}
