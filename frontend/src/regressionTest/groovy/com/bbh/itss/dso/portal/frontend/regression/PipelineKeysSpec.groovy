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

    def "the key history lists every key by its hint, with the reason of each invalidation"() {
        given:
        open('/admin/products/2')

        when:
        pipelineAction('mobile-app', 'SAST scanning', 'Key history')

        then:
        assertThat(dialog().locator('h2')).hasText('Key history of the pipeline mobile-app · SAST scanning')
        assertThat(dialog().locator('.intro')).hasText('Every key the pipeline of mobile-app in Payments Hub has had, by its first and last characters. Only the active key works; an invalidated key cannot be used again.')
        assertThat(gridHeaders(dialog())).hasText(['Key', 'Status', 'Issued', 'Last used by Jenkins', 'Invalidated'] as String[])
        assertThat(gridCells(dialog(), 'key').first()).hasText('dd3ac7a4…825e')
        assertThat(gridRows(dialog())).containsText(['Invalidated'] as String[])
        assertThat(dialog().locator('.reason')).hasText('Mobile app moved to the new mobile platform pipeline')
        assertThat(dialog().locator('.key-status')).containsText('Key invalidated')
        assertThat(dialogButton('Regenerate key')).isVisible()
        !(dialog().textContent() =~ KEY_VALUE)
        api.requests('GET', '/api/pipelines/9').size() == 1
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

    def "replacing a key asks first, issues a new key and shows it once"() {
        given:
        def replaced = fixture('pipeline-1.json') as Map
        def value = keyValue(41)
        def key = activeKey(41, value)
        replaced += [activeKey: key, keys: [key, revokedKey(replaced.activeKey as Map, 'Replaced by a new key', ISSUED_AT)]]
        api.respond('POST', '/api/pipelines/1/keys', replaced)
        open('/admin/products/1')

        when:
        pipelineAction('gui', 'Full', 'Replace key')

        then:
        assertThat(dialog().locator('h2')).hasText('Replace the key of the pipeline gui · Full?')
        assertThat(dialog().locator('.message')).containsText("Put the new key in the service's Jenkinsfile, or the pipeline is refused its settings")

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('POST', '/api/pipelines/1/keys').isEmpty()

        when:
        pipelineAction('gui', 'Full', 'Replace key')
        dialogButton('Replace key').click()

        then:
        assertThat(keyOf('gui', 'Full')).hasText(value)
        assertThat(snackBar()).containsText("New key issued for gui · Full. Put it in the service's Jenkinsfile.")
        awaitRequest('POST', '/api/pipelines/1/keys').json() == [:]
        assertThat(fact('Pipelines')).hasText('3, all active')
        ownErrors().isEmpty()
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

    def "an invalidated key is regenerated from the product page after a failed try, and the old key stays invalidated"() {
        given:
        def regenerated = fixture('pipeline-9-regenerated.json') as Map
        api.respond('POST', '/api/pipelines/9/keys', problem(503, 'Service Unavailable', 'The key store is being upgraded; try again in a minute'))
        open('/admin/products/2')
        def regenerate = pipelineButton('mobile-app', 'SAST scanning', 'Regenerate key of the SAST scanning pipeline')

        expect:
        assertThat(regenerate).hasText('Regenerate key')
        assertThat(regenerate.locator('svg-icon')).hasCount(0)
        assertThat(pipelineRow('mobile-app', 'SAST scanning').locator('.key-state')).hasText('Key invalidated')

        when:
        regenerate.click()

        then:
        assertThat(snackBar()).containsText('The key store is being upgraded; try again in a minute')
        assertThat(regenerate).isEnabled()
        assertThat(pipelineRow('mobile-app', 'SAST scanning').locator('.key-state')).hasText('Key invalidated')
        awaitRequest('POST', '/api/pipelines/9/keys').json() == [:]

        when:
        api.respond('POST', '/api/pipelines/9/keys', regenerated)
        api.respond('GET', '/api/pipelines/9', regenerated)
        regenerate.click()

        then:
        assertThat(keyOf('mobile-app', 'SAST scanning')).hasText(REGENERATED_KEY)
        assertThat(pipelineRow('mobile-app', 'SAST scanning').locator('.key-state')).hasText('Key active')
        assertThat(regenerate).hasCount(0)
        assertThat(fact('Pipelines')).hasText('6, all active')
        awaitRequest('POST', '/api/pipelines/9/keys', 2).json() == [:]
        api.requests('POST', '/api/pipelines/9/keys/revoke').isEmpty()

        when:
        pipelineAction('mobile-app', 'SAST scanning', 'Key history')

        then:
        assertThat(gridRows(dialog())).hasCount(2)
        keyRows([[hint(REGENERATED_KEY), 'Active'], ['dd3ac7a4…825e', 'Invalidated']])
        assertThat(dialog().locator('.reason')).hasText('Mobile app moved to the new mobile platform pipeline')
        assertThat(dialogButton('Regenerate key')).hasCount(0)
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    def "pipeline settings are edited with the type fixed"() {
        given:
        def updated = fixture('pipeline-1.json') as Map
        updated += [agentLabels: ['linux-agent', 'docker'], description: 'Main branch delivery', keys: null]
        api.respond('PUT', '/api/pipelines/1', updated)
        open('/admin/products/1')

        when:
        pipelineAction('gui', 'Full', 'Edit pipeline settings')

        then:
        assertThat(dialog().locator('h2')).hasText('Settings of the pipeline gui · Full')
        assertThat(select(dialog(), 'Pipeline type')).isDisabled()
        assertThat(input(dialog(), 'Jenkins job')).hasValue('DevSecOps/CERTSCANNER/gui-full')

        when:
        input(dialog(), 'Jenkins agents').fill('linux-agent, docker')
        input(dialog(), 'Description').fill('Main branch delivery')
        dialogButton('Save settings').click()

        then:
        assertThat(dialog()).hasCount(0)
        awaitRequest('PUT', '/api/pipelines/1').json() == [type      : 'FULL', agentLabels: ['linux-agent', 'docker'],
                                                               extendedPipelineJob: null, securityPipelineJob: null,
                                                               jenkinsJob: 'DevSecOps/CERTSCANNER/gui-full', description: 'Main branch delivery',
                                                               version   : 0]
        assertThat(pipelineRow('gui', 'Full').locator('.pipeline-meta')).containsText('linux-agent, docker')
        assertThat(pipelineRow('gui', 'Full').locator('.pipeline-description')).hasText('Main branch delivery')
        assertThat(snackBar()).containsText('Pipeline settings saved')
        ownErrors().isEmpty()
    }

    def "a pipeline and then the whole product are deleted after confirmation"() {
        given:
        api.respond('DELETE', '/api/pipelines/3', StubResponse.empty())
        api.respond('DELETE', '/api/products/1', StubResponse.empty())
        open('/admin/products/1')

        when:
        pipelineAction('backend-api', 'Full', 'Delete pipeline')

        then:
        assertThat(dialog().locator('h2')).hasText('Delete the pipeline backend-api · Full?')
        assertThat(dialog()).containsText('The full pipeline of backend-api and its key history are deleted.')
        assertThat(dialog()).containsText('This cannot be undone.')

        when:
        dialogButton('Delete pipeline').click()

        then:
        assertThat(serviceCard('backend-api').locator('.no-pipelines')).hasText('No pipeline yet, so Jenkins runs no checks for this service. Add one with Add pipeline.')
        assertThat(snackBar()).containsText('Pipeline backend-api · Full deleted.')
        awaitRequest('DELETE', '/api/pipelines/3') != null

        when:
        productAction('Delete product')

        then:
        assertThat(dialog().locator('h2')).hasText('Delete the product CertScanner?')
        assertThat(dialog()).containsText('This deletes CertScanner with its 2 services, 2 pipelines and their keys.')

        when:
        dialogButton('Delete product').click()
        page.waitForURL('**/admin/products')

        then:
        awaitRequest('DELETE', '/api/products/1') != null
        assertThat(snackBar()).containsText('CertScanner was deleted with its services and pipelines.')
        ownErrors().isEmpty()
    }
}
