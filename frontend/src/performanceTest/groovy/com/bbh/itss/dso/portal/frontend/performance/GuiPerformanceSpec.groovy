package com.bbh.itss.dso.portal.frontend.performance

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import spock.lang.Shared

import java.nio.file.Paths

import static com.bbh.itss.dso.portal.frontend.performance.LargeCatalogue.PIPELINES
import static com.bbh.itss.dso.portal.frontend.performance.LargeCatalogue.PRODUCTS
import static com.bbh.itss.dso.portal.frontend.performance.LargeCatalogue.SERVICES
import static com.bbh.itss.dso.portal.frontend.performance.LargeCatalogue.TYPES
import static com.microsoft.playwright.options.ReducedMotion.REDUCE
import static com.microsoft.playwright.options.WaitUntilState.COMMIT
import static java.lang.Math.ceil
import static java.nio.file.Files.createDirectories
import static java.nio.file.Files.writeString

class GuiPerformanceSpec extends DsoSpecification {

    static final int WARMUPS = 1
    static final int RUNS = 5
    static final double FACTOR = (System.getProperty('performance.factor') ?: '1') as double
    static final String READY = '''({ selector, count }) => {
        const found = document.querySelectorAll(selector);
        const shown = found.length >= count && found[count - 1].checkVisibility({ opacityProperty: true, visibilityProperty: true });
        return shown && performance.now();
    }'''
    static final String CLICK = 'element => { const start = performance.now(); element.click(); return start; }'

    @Shared
    LargeCatalogue catalogue = new LargeCatalogue()

    @Shared
    List<String> report = ['# Gui performance report', '',
                           "${PRODUCTS} products × ${SERVICES} services × ${TYPES.size()} pipelines; " +
                                   "${WARMUPS} warm-up and ${RUNS} measured runs per page; time from the click (or the navigation) until the " +
                                   "content is visible; limits × ${FACTOR} (-Dperformance.factor).", '',
                           '| Page | Visible when | Median | p95 | Limit | Result |', '| --- | --- | ---: | ---: | ---: | --- |']

    def setup() {
        catalogue.serve(api)
    }

    def cleanupSpec() {
        def target = Paths.get(System.getProperty('performance.report', 'build/reports/performance/gui-performance-report.md'))
        createDirectories(target.parent)
        writeString(target, report.join('\n') + '\n')
    }

    @Override
    Browser.NewContextOptions contextOptions() {
        super.contextOptions().setReducedMotion(REDUCE)
    }

    def "#scenario is visible within #limit ms at the 95th percentile"() {
        when:
        def times = (0..<WARMUPS + RUNS).collect { int run -> measure(from, click, 1 + (run * 7) % PRODUCTS, [selector: selector, count: count]) }
        def sorted = times.drop(WARMUPS).sort()
        def p95 = sorted[(int) ceil(0.95d * RUNS) - 1]
        report << "| $scenario | $count × `$selector` | ${Math.round(sorted[RUNS.intdiv(2)])} ms | ${Math.round(p95)} ms | ${Math.round(limit * FACTOR)} ms | ${p95 <= limit * FACTOR ? 'pass' : 'FAIL'} |".toString()

        then:
        p95 <= limit * FACTOR
        ownErrors().isEmpty()

        where:
        scenario                                   | limit | from                      | click                                            | selector                                      | count
        'Product list, cold start'                 | 2500  | null                      | null                                             | '.ag-center-cols-container .ag-row'           | PRODUCTS
        'Product page from the product list'       | 1500  | '/admin/products'         | "a.name[href='/admin/products/ID']"              | 'section.service .pipeline'                   | PIPELINES
        'Product editor from the product page'     | 2000  | '/admin/products/ID'      | "a[href='/admin/products/ID/edit']"              | 'dso-panel .service-name'                     | SERVICES
        'A service expanded in the editor'         | 500   | '/admin/products/ID/edit' | '.service-panel > h3 .accordion-button >> nth=8' | 'dso-panel.expanded dso-service-fields input' | 1
        'Monitoring overview from the menu'        | 1000  | '/admin/products'         | "nav.menu a[href='/monitoring']"                 | 'a.product'                                   | PRODUCTS
        "A product's monitoring from the overview" | 1000  | '/monitoring'             | "a.product[href='/monitoring/products/ID']"      | 'a.pipeline-link'                             | PIPELINES
    }

    double measure(String from, String click, int product, Map ready) {
        if (from) {
            page.navigate(url(from.replace('ID', "$product")))
            assert !page.evaluate(READY, ready)
            def start = page.locator(click.replace('ID', "$product")).evaluate(CLICK) as double
            return (page.waitForFunction(READY, ready).jsonValue() as double) - start
        }
        newPage()
        page.navigate(url('/admin/products'), new Page.NavigateOptions().setWaitUntil(COMMIT))
        page.waitForFunction(READY, ready).jsonValue() as double
    }
}
