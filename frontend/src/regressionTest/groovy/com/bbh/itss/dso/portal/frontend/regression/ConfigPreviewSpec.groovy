package com.bbh.itss.dso.portal.frontend.regression

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixtureText
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static java.nio.file.Files.readString

class ConfigPreviewSpec extends ProductPageSpecification {

    def "a pipeline's settings in the devsecops_config.yaml format are shown as the library receives them, and can be downloaded and copied"() {
        given:
        recordClipboard()
        def yaml = fixtureText('pipeline-1-config.yaml')
        open('/admin/products/1')

        when:
        pipelineAction('gui', 'Full', 'Settings sent to Jenkins (devsecops_config.yaml format)')

        then:
        assertThat(dialog().locator('h2')).hasText('Settings sent to Jenkins for gui · Full (devsecops_config.yaml format)')
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

    def "the product's settings are downloaded as devsecops_config.yaml, ready to commit for a Jenkinsfile without a key"() {
        given:
        open('/admin/products/1')

        when:
        productAction('Settings sent to Jenkins (devsecops_config.yaml)')

        then:
        assertThat(dialog().locator('h2')).hasText('Settings sent to Jenkins (devsecops_config.yaml) for CertScanner')
        assertThat(dialog().locator('.subtitle')).containsText('it also serves a Jenkinsfile without a pipeline key')

        when:
        def download = page.waitForDownload { dialogButton('Download').click() }

        then:
        download.suggestedFilename() == 'devsecops_config.yaml'
        readString(download.path()) == fixtureText('product-1-config.yaml')
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

    def "services whose pipelines share the Jenkins job of their repository are asked for one Jenkinsfile with their keys"() {
        given:
        def services = fixture('product-1-pipelines.json') as List<Map>
        open('/admin/products/1')

        expect: 'gui and backend-api share a repository but run in jobs of their own'
        assertThat(pipelineRow('backend-api', 'Full')).isVisible()
        assertThat(page.locator('.shared-repository')).hasCount(0)

        when:
        (services[1].pipelines as List<Map>)[0].jenkinsJob = 'DevSecOps/CERTSCANNER/gui-full'
        api.respond('GET', '/api/products/1/pipelines', services)
        open('/admin/products/1')

        then:
        assertThat(page.locator('.shared-repository .notice-text')).hasText([
                'gui and backend-api share the repository https://bitbucket.bbh.com/projects/TA/repos/cert-scanner ' +
                        'and one Jenkins job. Put one Jenkinsfile with the keys of their full pipelines there and ask for ' +
                        'one Jenkins job for these services, not one per service: one GoldenFix pull request then covers ' +
                        'all the services of the job. ' +
                        'Pipeline Monitoring shows the runs of the shared job under gui, the service of the first key. To see ' +
                        'them on the other services too, give them the same Project tag (Monitoring section) and the shared ' +
                        'job as the Jenkins job of their pipelines.'
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
