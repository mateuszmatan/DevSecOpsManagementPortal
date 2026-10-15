package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.ProductStore
import com.bbh.itss.dso.portal.frontend.support.StubResponse
import com.microsoft.playwright.Route

import java.util.function.Consumer

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class ApiFailureSpec extends EditorSpecification {

    static final String DETAIL = 'The database is not available; try again in a few minutes'

    def "#path shows the problem detail when #endpoint fails"() {
        given:
        api.respond('GET', endpoint, problem(500, 'Internal Server Error', DETAIL))

        when:
        open(path)

        then:
        assertThat(holdingText(page.locator('.banner'), DETAIL)).isVisible()
        ownErrors().every { it.contains('500') }

        where:
        path                      | endpoint
        '/admin/departments'      | '/api/departments'
        '/admin/products'         | '/api/products'
        '/admin/products'         | '/api/departments'
        '/admin/products/1'       | '/api/products/1'
        '/admin/products/1/edit'  | '/api/products/1/pipelines'
        '/monitoring'             | '/api/monitoring/products'
        '/monitoring/products/1'  | '/api/monitoring/products/1'
        '/monitoring/pipelines/1' | '/api/monitoring/pipelines/1'
        '/admin/settings'         | '/api/settings'
    }

    def "an error without a problem body still names the status"() {
        given:
        api.respond('GET', '/api/monitoring/products', new StubResponse(status: 502, contentType: 'text/html', body: '<html>Bad gateway</html>'))

        when:
        open('/monitoring')

        then:
        assertThat(holdingText(page.locator('.banner'), '502')).isVisible()
    }

    def "a page that failed to load offers the way back or another try"() {
        given:
        api.respond('GET', '/api/products/1', problem(404, 'Not Found', 'Product 1 was not found'))
        api.respond('GET', '/api/settings', problem(503, 'Service Unavailable', DETAIL))

        when:
        open('/admin/products/1')

        then:
        assertThat(page.locator('.banner')).hasText('The product could not be loaded. Product 1 was not found')
        assertThat(button('Try again', true)).isVisible()

        when:
        link('Back to products', true).click()
        page.waitForURL('**/admin/products')
        open('/admin/settings')

        then:
        assertThat(page.locator('.banner .banner-text')).hasText("The library defaults could not be loaded: $DETAIL")

        when:
        api.respond('GET', '/api/settings', fixture('settings.json'))
        button('Try again', true).click()

        then:
        assertThat(page.locator('.save-bar .saved')).containsText('(version 1)')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins.bbh.com')
    }

    def "a save that fails on the server keeps the editor and its changes"() {
        given:
        ProductStore.recorded(api, 1)
        api.respond('PUT', '/api/products/1', problem(500, 'Internal Server Error', DETAIL))
        open('/admin/products/1/edit')

        when:
        input(productFields(), 'Name').fill('CertScanner 2')
        button('Save changes', true).click()

        then:
        assertThat(saveError()).hasText("The product could not be saved. $DETAIL")
        page.url().endsWith('/admin/products/1/edit')
        assertThat(input(productFields(), 'Name')).hasValue('CertScanner 2')
        assertThat(button('Save changes', true)).isEnabled()
    }

    def "a save over a newer product is refused with the conflict the API reports"() {
        given:
        def store = ProductStore.recorded(api, 1)
        open('/admin/products/1/edit')
        store.product.version = 5

        when:
        input(productFields(), 'Name').fill('CertScanner 2')
        button('Save changes', true).click()

        then:
        assertThat(saveError()).hasText('The product could not be saved. The product was changed by someone else; reload it and try again')
        awaitRequest('PUT', '/api/products/1').json().version == 0
        page.url().endsWith('/admin/products/1/edit')
    }

    def "a portal API that cannot be reached is named"() {
        given:
        context.route('**/api/products', { Route route -> route.abort('connectionrefused') } as Consumer<Route>)

        when:
        open('/admin/products')

        then:
        assertThat(page.locator('.banner')).containsText('The product list could not be loaded. The portal cannot be reached. Check your network connection and try again.')
        assertThat(buttonIn(page.locator('.banner'), 'Try again')).isVisible()
    }
}
