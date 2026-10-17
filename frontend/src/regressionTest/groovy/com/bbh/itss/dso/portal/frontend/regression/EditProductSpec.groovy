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
        id << [1]
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
        ownErrors().isEmpty()
    }
}
