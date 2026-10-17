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
        path              | endpoint
        '/admin/products' | '/api/products'
        '/monitoring'     | '/api/monitoring/products'
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
