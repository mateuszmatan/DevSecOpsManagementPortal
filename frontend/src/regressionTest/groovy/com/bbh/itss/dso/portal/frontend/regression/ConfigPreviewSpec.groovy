package com.bbh.itss.dso.portal.frontend.regression

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixtureText
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static java.nio.file.Files.readString

class ConfigPreviewSpec extends ProductPageSpecification {

    def "a pipeline's config.yaml is shown as the library receives it, and can be downloaded and copied"() {
        given:
        recordClipboard()
        def yaml = fixtureText('pipeline-1-config.yaml')
        open('/admin/products/1')

        when:
        pipelineAction('gui', 'Full', 'Settings sent to Jenkins (config.yaml)')

        then:
        assertThat(dialog().locator('h2')).hasText('Settings sent to Jenkins for gui · Full (config.yaml)')
        assertThat(dialog().locator('.subtitle')).containsText('Opening it here does not count as a use of the key.')
        dialog().locator('pre.code-block').textContent() == yaml
        api.requests('GET', '/api/pipelines/1/config').size() == 1

        when:
        def download = page.waitForDownload { dialogButton('Download').click() }

        then:
        download.suggestedFilename() == 'certscanner-gui-full.yaml'
        readString(download.path()) == yaml

        when:
        dialogButton('Copy').click()

        then:
        copiedTexts() == [yaml]
        assertThat(snackBar()).containsText('Copied to the clipboard')

        when:
        dialogButton('Close').click()

        then:
        assertThat(dialog()).hasCount(0)
        ownErrors().isEmpty()
    }

    def "the Jenkinsfile of a pipeline loads the shared library of the global settings with the pipeline's key"() {
        given:
        def settings = fixture('settings.json') as Map
        settings.platform.jenkinsLibrary = 'BbhDevSecOps@2.4'
        api.respond('GET', '/api/settings', settings)
        open('/admin/products/1')

        when:
        pipelineAction('gui', 'SAST scanning', 'Jenkinsfile')

        then:
        assertThat(dialog().locator('h2')).hasText('Jenkinsfile of gui · SAST scanning')
        assertThat(dialog().locator('.subtitle')).hasText("The file to put in the service's repository so Jenkins runs this pipeline. It holds only the pipeline key: Jenkins fetches every other setting from this portal.")
        dialog().locator('pre.code-block').textContent() ==
                "@Library('BbhDevSecOps@2.4') _\n\ndevSecOpsSASTScanningPipeline(pipelineKey: '2c0ca4f4-a1a6-472a-9685-0c75f22fe713')\n"
        ownErrors().isEmpty()
    }

    def "services that share a repository are asked for one Jenkinsfile with their keys and one Jenkins job"() {
        given:
        open('/admin/products/1')

        expect:
        assertThat(page.locator('.shared-repository .notice-text')).hasText([
                'gui and backend-api share the repository https://bitbucket.bbh.com/projects/TA/repos/cert-scanner. ' +
                        'Put one Jenkinsfile with the keys of their full pipelines there and ask for one Jenkins job for the ' +
                        'repository, not one per service: one GoldenFix pull request then raises the shared catalog once.'
        ] as String[])

        when:
        buttonIn(page.locator('.shared-repository'), 'Show the Jenkinsfile').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Jenkinsfile for several services')
        assertThat(dialog().locator('.subtitle')).containsText('One run builds gui, backend-api;')
        dialog().locator('pre.code-block').textContent() ==
                "@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsPipeline(pipelineKeys: [\n" +
                "    '7b62170e-5c42-4bec-8cbc-035977e3299e',\n    'c44f8956-8e07-4ac8-b801-d6a1222a9c1f',\n])\n"
        ownErrors().isEmpty()
    }
}
