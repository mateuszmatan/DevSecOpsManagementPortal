package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.StubResponse

import java.util.regex.Pattern

import static com.bbh.itss.dso.portal.frontend.support.ApiData.ISSUED_AT
import static com.bbh.itss.dso.portal.frontend.support.ApiData.activeKey
import static com.bbh.itss.dso.portal.frontend.support.ApiData.hint
import static com.bbh.itss.dso.portal.frontend.support.ApiData.keyValue
import static com.bbh.itss.dso.portal.frontend.support.ApiData.pipeline
import static com.bbh.itss.dso.portal.frontend.support.ApiData.revokedKey
import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class PipelineKeysSpec extends ProductPageSpecification {

    static final String GUI_FULL_KEY = '7b62170e-5c42-4bec-8cbc-035977e3299e'
    static final String REGENERATED_KEY = '3f9d2c4e-8a1b-4c7d-9e2f-5b6a7c8d1e04'
    static final Pattern KEY_VALUE = ~/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/

    def "keys show by their hint until shown, and are copied whole"() {
        given:
        recordClipboard()
        open('/admin/products/1')

        expect:
        assertThat(keyOf('gui', 'Full')).hasText('7b62170e…299e')
        assertThat(keyOf('gui', 'SAST scanning')).hasText('2c0ca4f4…e713')
        !page.locator('main').textContent().contains(GUI_FULL_KEY)

        when:
        pipelineButton('gui', 'Full', 'Show the key of the full pipeline').click()

        then:
        assertThat(pipelineButton('gui', 'Full', 'Hide the key of the full pipeline')).hasText('Hide key')
        assertThat(keyOf('gui', 'Full')).hasText(GUI_FULL_KEY)
        assertThat(keyOf('gui', 'SAST scanning')).hasText('2c0ca4f4…e713')

        when:
        pipelineButton('gui', 'Full', 'Hide the key of the full pipeline').click()
        pipelineButton('gui', 'Full', 'Copy the key of the full pipeline').click()

        then:
        assertThat(keyOf('gui', 'Full')).hasText('7b62170e…299e')
        assertThat(snackBar()).containsText('Key copied to the clipboard')
        copiedTexts() == [GUI_FULL_KEY]
        ownErrors().isEmpty()
    }

    def "a pipeline is added to a service with the types it lacks, after its field errors are fixed"() {
        given:
        def created = pipeline(id: 20, productId: 1, serviceId: 1, serviceName: 'gui', type: 'SECURITY',
                entryPoint: 'devSecOpsSecurityPipeline', agentLabels: ['linux-agent', 'docker'],
                extendedPipelineJob: 'DevSecOps/CERTSCANNER/gui-extended',
                jenkinsJob: 'DevSecOps/CERTSCANNER/gui-security-scan',
                jenkinsJobUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-security-scan/',
                description: 'Nightly security scan', activeKey: activeKey(30, keyValue(30)),
                influxProjectTag: 'CERTSCANNER-guisecurity')
        api.respond('POST', '/api/services/1/pipelines', problem(400, 'Bad Request', 'Some values are not valid',
                [errors: [[field: 'jenkinsJob', message: 'another pipeline already runs in this Jenkins job']]]))
        open('/admin/products/1')

        when:
        buttonIn(serviceCard('gui'), 'Add pipeline', false).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Add a pipeline to gui')
        optionsOf(dialog(), 'Pipeline type') == ['Security', 'Extended', 'Nexus IQ GoldenFix']

        when:
        choose(dialog(), 'Pipeline type', 'Security')

        then:
        assertThat(input(dialog(), 'Jenkins job')).hasValue('DevSecOps/CERTSCANNER/gui-security')
        assertThat(input(dialog(), 'Jenkins agents')).hasValue('linux-agent')

        when:
        input(dialog(), 'Jenkins agents').fill('linux-agent, docker')
        input(dialog(), 'Jenkins job').fill('DevSecOps/CERTSCANNER/gui-security')
        input(dialog(), 'Extended pipeline job').fill('DevSecOps/../gui-extended')
        input(dialog(), 'Description').fill('Nightly security scan')
        dialogButton('Add pipeline').click()

        then:
        assertThat(errorOf(dialog(), 'Extended pipeline job')).hasText("A job path such as DevSecOps/CERT/backend-api-extended, without '..'")
        api.requests('POST', '/api/services/1/pipelines').isEmpty()

        when:
        input(dialog(), 'Extended pipeline job').fill('DevSecOps/CERTSCANNER/gui-extended')
        dialogButton('Add pipeline').click()

        then:
        assertThat(errorOf(dialog(), 'Jenkins job')).hasText('another pipeline already runs in this Jenkins job')
        assertThat(dialog()).isVisible()

        when:
        api.respond('POST', '/api/services/1/pipelines', created, 201)
        input(dialog(), 'Jenkins job').fill('DevSecOps/CERTSCANNER/gui-security-scan')
        dialogButton('Add pipeline').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('POST', '/api/services/1/pipelines').size() == 2
        api.lastRequest('POST', '/api/services/1/pipelines').json() == [
                type               : 'SECURITY', agentLabels: ['linux-agent', 'docker'],
                extendedPipelineJob: 'DevSecOps/CERTSCANNER/gui-extended', securityPipelineJob: null,
                jenkinsJob         : 'DevSecOps/CERTSCANNER/gui-security-scan', description: 'Nightly security scan',
                version            : null]
        assertThat(pipelineTypes('gui')).hasText(['Full pipeline', 'Security pipeline', 'SAST scanning pipeline'] as String[])
        assertThat(keyOf('gui', 'Security')).hasText(hint(keyValue(30)))
        assertThat(snackBar()).containsText('Security pipeline added to gui')
        assertThat(fact('Pipelines')).hasText('4, all active')
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "a key invalidated with a reason is regenerated from its history, and the old key stays invalidated"() {
        given:
        def original = fixture('pipeline-2.json') as Map
        def oldKey = original.activeKey as Map
        def revoked = original + [enabled: false, activeKey: null, keys: null]
        def history = revoked + [keys: [revokedKey(oldKey, 'Key printed in a build log', '2026-10-05T09:30:00Z')]]
        def newValue = keyValue(42)
        def newKey = activeKey(42, newValue, '2026-10-05T09:45:00Z')
        def regenerated = original + [enabled: true, activeKey: newKey, keys: [newKey] + (history.keys as List)]
        api.respond('POST', '/api/pipelines/2/keys/revoke', revoked)
        api.respond('GET', '/api/pipelines/2', history)
        api.respond('POST', '/api/pipelines/2/keys', regenerated)
        open('/admin/products/1')

        when:
        pipelineAction('gui', 'SAST scanning', 'Invalidate key')

        then:
        assertThat(dialog().locator('h2')).hasText('Invalidate the key of the pipeline gui · SAST scanning?')
        assertThat(dialog().locator('.banner')).containsText('The SAST scanning pipeline of gui is refused its settings from now on and stops at its next start.')

        when:
        dialogButton('Invalidate key').click()

        then:
        assertThat(errorOf(dialog(), 'Reason')).hasText('Required')
        api.requests('POST', '/api/pipelines/2/keys/revoke').isEmpty()

        when:
        input(dialog(), 'Reason').fill('  Key printed in a build log  ')
        dialogButton('Invalidate key').click()

        then:
        assertThat(dialog()).hasCount(0)
        awaitRequest('POST', '/api/pipelines/2/keys/revoke').json() == [reason: 'Key printed in a build log']
        assertThat(snackBar()).containsText('Key invalidated. The pipeline gui · SAST scanning is refused its settings and stops at its next start.')
        assertThat(pipelineRow('gui', 'SAST scanning').locator('.key-state')).hasText('Key invalidated')
        assertThat(pipelineRow('gui', 'SAST scanning').locator('.revoked-note'))
                .hasText("Jenkins is refused this pipeline's settings, so it stops until a new key is issued.")
        assertThat(pipelineButton('gui', 'SAST scanning', 'Regenerate key of the SAST scanning pipeline')).isVisible()
        assertThat(fact('Pipelines')).hasText('3, 1 key invalidated')

        when:
        pipelineAction('gui', 'SAST scanning', 'Key history')
        dialogButton('Regenerate key').click()

        then:
        assertThat(dialog().locator('.key-status')).containsText('New key')
        assertThat(dialog().locator('.key-status .key-value')).hasText(newValue)
        assertThat(gridRows(dialog())).hasCount(2)
        keyRows([[hint(newValue), 'Active'],
                 ['2c0ca4f4…e713', 'Invalidated', 'Key printed in a build log']])
        awaitRequest('POST', '/api/pipelines/2/keys').json() == [:]

        when:
        dialogButton('Close').click()

        then:
        assertThat(keyOf('gui', 'SAST scanning')).hasText(newValue)
        assertThat(pipelineRow('gui', 'SAST scanning').locator('.key-state')).hasText('Key active')
        assertThat(button('Regenerate key')).hasCount(0)
        assertThat(fact('Pipelines')).hasText('3, all active')
        api.requests('POST', '/api/pipelines/2/keys').size() == 1
        ownErrors().isEmpty()
    }
}
