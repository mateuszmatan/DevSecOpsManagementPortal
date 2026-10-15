package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.bbh.itss.dso.portal.frontend.support.RecordedRequest
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.MENUITEM

class PipelinesSpec extends DsoSpecification {

    static final String GUI_FULL_KEY = '7b62170e-5c42-4bec-8cbc-035977e3299e'
    static final String REGENERATED_KEY = '3f9d2c4e-8a1b-4c7d-9e2f-5b6a7c8d1e04'

    def "a developer picks the department and filters and sorts its pipelines in the table header"() {
        when:
        open('/monitoring')
        menuLink('Pipelines').click()
        page.waitForURL('**/pipelines')

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Pipelines')
        assertThat(page.locator('.page-header .page-description'))
                .hasText('Every automated build, test and security pipeline of your department\'s products. Open one to see its key, its Jenkinsfile and its latest runs.')
        assertThat(page.locator('.empty-state h3')).hasText('Choose your department to see its pipelines')
        assertThat(page.locator('.toolbar dso-hint')).hasText('Only the pipelines of this department are listed. This browser remembers your choice.')
        api.requests('GET', '/api/pipelines').isEmpty()

        when:
        choose(page.locator('.toolbar'), 'Your department', 'Fund Services')

        then:
        assertThat(column('service')).hasText(['gateway', 'gateway', 'gateway', 'ledger', 'notifications', 'mobile-app'] as String[])
        awaitRequest('GET', '/api/pipelines').params() == [departmentId: '5']
        assertThat(gridHeaders()).hasText(['Service', 'Product', 'Type', 'Jenkins job', 'Pipeline key', 'Last run', 'Finished', ''] as String[])
        assertThat(page.locator('.list h2')).hasText('Pipelines of Fund Services')
        assertThat(column('key')).hasText(['50b4ada7…108f', '9e9f17e8…4790', '5a07b656…5627', 'fa529f98…a726',
                                           '26b4c145…64e4', 'Invalidated'] as String[])
        assertThat(column('status')).hasText(['Passed', 'Passed', 'Passed', 'Passed with warnings', 'Passed with warnings', 'Key invalidated'] as String[])
        assertThat(page.locator('.list .shown')).hasText('6 pipelines')
        assertThat(link('gateway').first()).hasAttribute('href', '/pipelines/6')
        !page.locator('main').textContent().contains('5a07b656-')

        when:
        gridFilter('service').fill('gate')

        then:
        assertThat(column('type')).hasText(['Extended', 'Full', 'Security'] as String[])
        assertThat(page.locator('.list .shown')).hasText('3 of 6 pipelines shown')

        when:
        gridFilter('service').fill('')
        choose(page.locator('.ag-floating-filter'), 'Filter by pipeline key', 'Invalidated')

        then:
        assertThat(column('service')).hasText(['mobile-app'] as String[])

        when:
        choose(page.locator('.ag-floating-filter'), 'Filter by pipeline key', 'All')
        choose(page.locator('.ag-floating-filter'), 'Filter by last run', 'Passed with warnings')
        gridFilter('Jenkins job').fill('ledger')

        then:
        assertThat(column('service')).hasText(['ledger'] as String[])

        when:
        gridFilter('product').fill('certscanner')

        then:
        assertThat(page.locator('.dso-grid-empty')).hasText('No pipeline matches the filters.')

        when:
        gridFilter('product').fill('')
        gridFilter('Jenkins job').fill('')
        choose(page.locator('.ag-floating-filter'), 'Filter by last run', 'All')
        sortBy('type')

        then:
        assertThat(column('type')).hasText(['Full', 'Full', 'Full', 'Security', 'Extended', 'SAST scanning'] as String[])

        when:
        sortBy('type')

        then:
        assertThat(column('type').first()).hasText('SAST scanning')

        when:
        open('/pipelines')

        then:
        assertThat(select(page.locator('.toolbar'), 'Your department').locator('option:checked')).hasText('Fund Services')
        assertThat(column('service')).hasCount(6)

        when:
        choose(page.locator('.toolbar'), 'Your department', 'Custody')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No pipelines in Custody yet')
        assertThat(link('Set up pipelines in Self-service', true)).hasCount(2)
        assertThat(page.locator('.empty-state a')).hasAttribute('href', '/self-service')
        ownErrors().isEmpty()
    }

    def "a row opens the page of its pipeline with its key, settings, Jenkinsfile and recent runs"() {
        given:
        page.addInitScript("localStorage.setItem('dso.beadle.department', '3')")
        recordClipboard()

        when:
        open('/pipelines')
        column('service').first().click()
        page.waitForURL('**/pipelines/1')

        then:
        assertThat(page.locator('.breadcrumb a, .breadcrumb span:not(.sep)'))
                .hasText(['DevSecOps Pipelines', 'CertScanner', 'gui · Full'] as String[])
        assertThat(page.locator('h1')).hasText('gui · Full pipeline')
        assertThat(page.locator('.title .last-run .muted')).hasText('Last run')
        assertThat(page.locator('.title .last-run .chip')).hasText('Passed')
        assertThat(page.locator('.key-value')).hasText('7b62170e…299e')
        assertThat(fact('Jenkins agents').locator('.mono')).hasText('linux-agent')
        assertThat(fact('Jenkins job')).hasText('DevSecOps/CERTSCANNER/gui-full')
        assertThat(page.locator('.jenkinsfile .section-help')).containsText("The file to put in the service's repository so Jenkins runs this pipeline.")
        assertThat(page.locator('pre.code-block')).containsText("devSecOpsPipeline(pipelineKey: '${GUI_FULL_KEY}')")
        assertThat(gridRows()).hasCount(5)
        assertThat(link('See every run and the delivery performance (DORA)', true)).hasAttribute('href', '/monitoring/pipelines/1')
        assertThat(link('Open in Jenkins', true)).hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')
        assertThat(page.locator('.page-header .btn-primary')).hasText('Edit settings')

        when:
        button('More', true).click()

        then:
        assertThat(menuItem('Open CertScanner in Admin')).hasAttribute('href', '/admin/products/1')

        when:
        page.keyboard().press('Escape')
        button('Show the key', true).click()

        then:
        assertThat(page.locator('.key-value')).hasText(GUI_FULL_KEY)

        when:
        button('Copy the key', true).click()

        then:
        assertThat(snackBar()).containsText('Key copied to the clipboard')
        copiedTexts() == [GUI_FULL_KEY]

        when:
        button('Copy the Jenkinsfile', true).click()

        then:
        assertThat(snackBar()).containsText('Jenkinsfile copied to the clipboard')
        copiedTexts() == [GUI_FULL_KEY, "@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsPipeline(pipelineKey: '${GUI_FULL_KEY}')\n".toString()]
        ownErrors().isEmpty()
    }

    def "Edit saves the settings of a pipeline and the list keeps its masked key"() {
        given:
        def saved = fixture('pipeline-1.json') as Map
        saved.agentLabels = ['linux-agent', 'docker']
        saved.description = 'Release build'
        api.on('PUT', '/api/pipelines/1') { saved }

        when:
        open('/pipelines/1')
        button('Edit settings', true).click()
        input(dialog(), 'Jenkins agents').fill('linux-agent, docker')
        input(dialog(), 'Description').fill('Release build')
        dialogButton('Save settings').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Pipeline settings saved. Jenkins uses them the next time the pipeline runs.')
        assertThat(fact('Jenkins agents').locator('.mono')).hasText('linux-agent, docker')
        assertThat(fact('Description')).hasText('Release build')
        with(awaitRequest('PUT', '/api/pipelines/1').json()) {
            agentLabels == ['linux-agent', 'docker']
            description == 'Release build'
            type == 'FULL'
            version == 0
        }

        when:
        page.addInitScript("localStorage.setItem('dso.beadle.department', '3')")
        open('/pipelines')
        buttonIn(page.locator('dso-grid'), 'Edit the settings of the Full pipeline of gui').click()
        input(dialog(), 'Description').fill('Release build')
        dialogButton('Save settings').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(column('key').first()).hasText('7b62170e…299e')
        api.requests('PUT', '/api/pipelines/1').size() == 2
        ownErrors().isEmpty()
    }

    def "a pipeline someone else changed meanwhile is reloaded in its dialog and saved on top"() {
        given:
        def current = (fixture('pipeline-1.json') as Map) + [version: 1, jenkinsJob: 'DevSecOps/CERTSCANNER/gui-full-v2']
        api.get('/api/pipelines/1') { api.requests('PUT', '/api/pipelines/1') ? current : fixture('pipeline-1.json') }
        api.on('PUT', '/api/pipelines/1') { RecordedRequest request ->
            request.json().version == current.version ? current + [description: request.json().description]
                    : problem(409, 'Conflict', 'The record was changed by someone else in the meantime. Reload it and apply your change again.')
        }

        when:
        open('/pipelines/1')
        button('Edit settings', true).click()
        input(dialog(), 'Description').fill('Release build')
        dialogButton('Save settings').click()

        then:
        assertThat(dialog().locator('[role=alert]')).hasText('Not saved: someone else changed the settings of this pipeline after you opened them. ' +
                'The form now shows their settings: make your change again, then save.')
        assertThat(input(dialog(), 'Jenkins job')).hasValue('DevSecOps/CERTSCANNER/gui-full-v2')
        assertThat(input(dialog(), 'Description')).hasValue('')

        when:
        input(dialog(), 'Description').fill('Release build')
        dialogButton('Save settings').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(fact('Jenkins job')).hasText('DevSecOps/CERTSCANNER/gui-full-v2')
        assertThat(fact('Description')).hasText('Release build')
        api.requests('PUT', '/api/pipelines/1')*.json()*.version == [0, 1]
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "the latest runs say why they could not be loaded and load on a second try"() {
        given:
        def failing = true
        api.get('/api/monitoring/pipelines/1') {
            failing ? problem(500, 'Internal Server Error', 'The run history is not available') : fixture('monitoring-pipeline-1.json')
        }

        when:
        open('/pipelines/1')

        then:
        assertThat(page.locator('.runs .banner')).containsText('The latest runs could not be loaded. The run history is not available')
        assertThat(page.locator('.runs .small-empty')).hasCount(0)

        when:
        failing = false
        buttonIn(page.locator('.runs'), 'Try again').click()

        then:
        assertThat(gridRows()).hasCount(5)
        assertThat(page.locator('.runs .banner')).hasCount(0)
        ownErrors().findAll { !it.contains('500') }.isEmpty()
    }

    def "an invalidated key is regenerated on the page of its pipeline"() {
        given:
        api.respond('POST', '/api/pipelines/9/keys', fixture('pipeline-9-regenerated.json'))
        api.get('/api/monitoring/pipelines/9') {
            def monitoring = fixture('monitoring-pipeline-9.json') as Map
            api.requests('POST', '/api/pipelines/9/keys') ? monitoring + [status: 'SUCCESS'] : monitoring
        }

        when:
        open('/pipelines/9')

        then:
        assertThat(page.locator('.banner.danger')).containsText('The pipeline key is invalidated. Jenkins is refused the settings of this pipeline')
        assertThat(page.locator('.title .last-run .chip')).hasText('Key invalidated')
        assertThat(page.locator('pre.code-block')).containsText('<issue a new key first>')

        when:
        button('Regenerate key', true).click()

        then:
        assertThat(page.locator('.key-value')).hasText(REGENERATED_KEY)
        assertThat(page.locator('.banner.danger')).hasCount(0)
        assertThat(page.locator('pre.code-block')).containsText(REGENERATED_KEY)
        assertThat(page.locator('.title .last-run .chip')).hasText('Passed')
        assertThat(snackBar()).containsText('SAST scanning pipeline of mobile-app has a new key')
        api.requests('POST', '/api/pipelines/9/keys').size() == 1
        ownErrors().isEmpty()
    }

    def "an unknown pipeline leads back to the list"() {
        when:
        open('/pipelines/99')

        then:
        assertThat(page.locator('.banner')).hasText('The pipeline could not be loaded. Pipeline 99 does not exist')

        when:
        link('Back to pipelines', true).click()

        then:
        page.waitForURL('**/pipelines')
        assertThat(page.locator('h1')).hasText('DevSecOps Pipelines')
    }

    Locator column(String name) {
        gridCells(page.locator('body'), name)
    }

    Locator fact(String label) {
        holding(page.locator('.rows > div'), "dt:text-is('${label}')").locator('dd')
    }

    Locator menuItem(String name) {
        page.getByRole(MENUITEM, new Page.GetByRoleOptions().setName(name).setExact(true))
    }
}
