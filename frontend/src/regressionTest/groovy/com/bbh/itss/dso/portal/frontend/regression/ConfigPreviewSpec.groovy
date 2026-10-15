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

    def "the product's config.yaml holds every service of the product"() {
        given:
        def yaml = fixtureText('product-1-config.yaml')
        open('/admin/products/1')

        when:
        productAction('Settings sent to Jenkins (config.yaml)')

        then:
        assertThat(dialog().locator('h2')).hasText('Settings sent to Jenkins (config.yaml) for CertScanner')
        dialog().locator('pre.code-block').textContent() == yaml

        when:
        def download = page.waitForDownload { dialogButton('Download').click() }

        then:
        download.suggestedFilename() == 'certscanner-config.yaml'
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

    def "a failed #what preview shows the problem detail the API sends"() {
        given:
        api.respond('GET', path, problem(status, title, detail))
        open('/admin/products/1')

        when:
        action.call(this)

        then:
        assertThat(snackBar()).containsText(detail)
        assertThat(dialog()).hasCount(0)
        api.requests('GET', path).size() == 1

        where:
        what                | path                      | status | title                   | detail                                                             | action
        'pipeline config'   | '/api/pipelines/3/config' | 404    | 'Not Found'             | 'Pipeline 3 was not found'                                         | { ConfigPreviewSpec spec -> spec.pipelineAction('backend-api', 'Full', 'Settings sent to Jenkins (config.yaml)') }
        'product config'    | '/api/products/1/config'  | 500    | 'Internal Server Error' | 'The configuration of CertScanner cannot be rendered: no services' | { ConfigPreviewSpec spec -> spec.productAction('Settings sent to Jenkins (config.yaml)') }
    }
}
