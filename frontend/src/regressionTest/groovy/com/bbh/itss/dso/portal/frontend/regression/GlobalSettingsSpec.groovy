package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.bbh.itss.dso.portal.frontend.support.RecordedRequest
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixtureText
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.json
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.CHECKBOX

class GlobalSettingsSpec extends DsoSpecification {

    static final String SAVED_AT = '2026-10-05T11:00:00Z'

    static final String SHOW_CONFIG = 'Show settings sent to Jenkins'

    Map stored

    def setup() {
        stored = fixture('settings.json') as Map
        api.get('/api/settings') { json(stored) }
        api.on('PUT', '/api/settings') { RecordedRequest request ->
            def body = request.json() as Map
            if (body.version != stored.version) {
                return problem(409, 'Conflict', 'The settings were changed by someone else')
            }
            stored = body + [version: (stored.version as int) + 1, updatedAt: SAVED_AT]
            json(stored)
        }
    }

    def "saving the settings unchanged sends back what was loaded with its version"() {
        given:
        def loaded = fixture('settings.json') as Map
        open('/admin/settings')

        expect:
        assertThat(page.locator('.tab-header .lead')).hasText(
                'Only change these settings if the DevSecOps team asks you to. They apply to every pipeline from its next run.')
        assertThat(saved()).containsText('(version 1)')

        when:
        button('Save settings', true).click()

        then:
        assertThat(snackBar()).containsText('Library defaults saved. Every pipeline gets them the next time it runs.')
        awaitRequest('PUT', '/api/settings').json() == loaded.findAll { it.key != 'updatedAt' }
        assertThat(saved()).containsText('(version 2)')
        ownErrors().isEmpty()
    }

    def "an edited setting is saved with the loaded version and the page shows the saved values"() {
        given:
        open('/admin/settings')

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        page.getByRole(CHECKBOX, new Page.GetByRoleOptions().setName('GoldenFix runs by default')).uncheck()

        then:
        assertThat(page.locator('.save-bar')).containsText('Unsaved changes')

        when:
        button('Save settings', true).click()

        then:
        def body = awaitRequest('PUT', '/api/settings').json() as Map
        body.version == 1
        body.platform.jenkinsUrl == 'https://jenkins2.bbh.com/'
        body.goldenFix.enabled == false
        body.findAll { !(it.key in ['platform', 'goldenFix']) } ==
                (fixture('settings.json') as Map).findAll { !(it.key in ['platform', 'goldenFix', 'updatedAt']) }
        assertThat(saved()).containsText('(version 2)')
        assertThat(page.locator('.save-bar')).not().containsText('Unsaved changes')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins2.bbh.com/')
        ownErrors().isEmpty()
    }

    def "a save over someone else's newer settings is refused with a conflict message and recovered by reloading"() {
        given:
        open('/admin/settings')
        stored = stored + [version: 2, updatedAt: SAVED_AT, platform: (stored.platform as Map) + [oisHost: 'ois2.bbh.com']]

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button('Save settings', true).click()

        then:
        assertThat(page.locator('.banner.conflict')).containsText('Someone else saved the settings after you opened this page.')
        assertThat(page.locator('.banner.conflict')).containsText('Your changes were not saved, so nothing was overwritten.')
        assertThat(page.locator('.save-bar .save-error')).hasText('Not saved: the settings were changed by someone else.')
        assertThat(button('Save settings', true)).isDisabled()
        awaitRequest('PUT', '/api/settings').json().version == 1

        when:
        buttonIn(page.locator('.banner.conflict'), 'Reload', false).click()

        then:
        assertThat(page.locator('.banner.conflict')).hasCount(0)
        assertThat(field('OIS host')).hasValue('ois2.bbh.com')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins.bbh.com')
        assertThat(saved()).containsText('(version 2)')
        assertThat(button('Save settings', true)).isEnabled()

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button('Save settings', true).click()

        then:
        assertThat(snackBar()).containsText('Library defaults saved. Every pipeline gets them the next time it runs.')
        awaitRequest('PUT', '/api/settings', 2).json().version == 2
        stored.version == 3
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "the server's field errors are shown on their fields and the section is marked"() {
        given:
        api.respond('PUT', '/api/settings', problem(400, 'Bad Request', 'Some values are not valid', [errors: [
                [field: 'platform.sonarServerUrl', message: 'SonarQube does not answer at this address'],
                [field: 'audit.retentionDays', message: 'must be at least 30']]]))
        open('/admin/settings')

        when:
        button('Save settings', true).click()

        then:
        assertThat(page.locator('.save-bar .save-error')).hasText('The portal did not accept some values. They are marked below.')
        assertThat(errorOf(page.locator('form'), 'SonarQube server URL')).hasText('SonarQube does not answer at this address')
        assertThat(page.locator('.problems li')).hasText(['audit.retentionDays: must be at least 30'] as String[])
        assertThat(page.locator('.toc-item.problem')).hasText(['Tools and servers'] as String[])

        when:
        field('SonarQube server URL').fill('https://sonar.bbh.com')

        then:
        assertThat(page.locator('.toc-item.problem')).hasCount(0)
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "changes are discarded with the button or when leaving the page after confirming"() {
        given:
        open('/admin/settings')

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button('Discard changes', true).click()

        then:
        assertThat(field('Jenkins URL')).hasValue('https://jenkins.bbh.com')
        assertThat(button('Discard changes', true)).isDisabled()

        when:
        field('Proxy host').fill('proxy2.bbh.com')
        menuLink('Admin').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialogButton('Discard').click()
        page.waitForURL('**/admin/products')

        then:
        api.requests('PUT', '/api/settings').isEmpty()
        ownErrors().isEmpty()
    }

    def "the settings sent to Jenkins are the saved global section, and a failure names its problem"() {
        given:
        open('/admin/settings')

        when:
        button(SHOW_CONFIG, true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Shared settings sent to Jenkins (config.yaml)')
        dialog().locator('pre.code-block').textContent() == fixtureText('settings-config.yaml')
        awaitRequest('GET', '/api/settings/config').params() == [format: 'yaml']

        when:
        dialogButton('Close').click()
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button(SHOW_CONFIG, true).click()

        then:
        assertThat(dialog().locator('.subtitle')).containsText('Your unsaved changes are not included.')

        when:
        dialogButton('Close').click()
        api.respond('GET', '/api/settings/config', problem(503, 'Service Unavailable', 'The configuration renderer is restarting'))
        button(SHOW_CONFIG, true).click()

        then:
        assertThat(snackBar()).containsText('The configuration renderer is restarting')
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    Locator saved() {
        page.locator('.save-bar .saved')
    }
}
