package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.DsoSpecification
import com.bbh.itss.dso.portal.frontend.support.RecordedRequest
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.json
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.LINK
import static com.microsoft.playwright.options.AriaRole.RADIOGROUP

class MonitoringSpec extends DsoSpecification {

    def "the overview totals every pipeline and filters products in the browser"() {
        given:
        open('/monitoring')

        expect:
        assertThat(stat('Pipelines')).hasText('9')
        assertThat(stat('Passed')).hasText('6')
        assertThat(stat('Failed or passed with warnings')).hasText('2')
        assertThat(stat('Keys invalidated')).hasText('1')
        assertThat(page.locator('.department-title h3')).hasText(['Corporate Technology', 'Fund Services'] as String[])
        assertThat(productCards()).hasText(['CertScanner', 'Payments Hub'] as String[])

        when:
        filter().fill('PAYMENTS')

        then:
        assertThat(productCards()).hasText(['Payments Hub'] as String[])

        when:
        filter().fill('technology')

        then:
        assertThat(productCards()).hasText(['CertScanner'] as String[])

        when:
        filter().fill('zzz')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No product matches "zzz"')
        api.requests('GET', '/api/monitoring/products').size() == 1

        when:
        filter().fill('')
        holdingText(page.locator('a.product'), 'Payments Hub').click()
        page.waitForURL('**/monitoring/products/2')

        then:
        assertThat(page.locator('h1')).hasText('Payments Hub')
        ownErrors().isEmpty()
    }

    def "the overview charts the DORA metrics, the daily runs and the status of every department"() {
        when:
        open('/monitoring')

        then:
        assertThat(page.locator('dso-dora-tiles h2')).hasText('Delivery performance (DORA)')
        assertThat(page.locator('dso-dora-tiles .tile-meaning')).hasText(['How often a change reaches production',
                                                                         'How long a change takes from commit to production',
                                                                         'Share of deployments that failed',
                                                                         'How long it takes to recover after a failed deployment'] as String[])
        assertThat(page.locator('dso-dora-tiles .tile-value')).hasText(['1.4 / day', '41h 33m', '29.0%', '13h 24m'] as String[])
        assertThat(page.locator('.portfolio .card-header .muted')).hasText('62 runs of all pipelines in the last 30 days')
        assertThat(page.locator('.portfolio .highcharts-series.runs .highcharts-point').first()).isVisible()
        assertThat(page.locator('.portfolio .legend span')).hasText(['Other runs', 'Failed deployments', 'Deployed that day'] as String[])
        page.locator('.portfolio .highcharts-markers.deployment .highcharts-point').first()
                .evaluate('point => getComputedStyle(point).fill') ==
                page.locator('.portfolio .legend .swatch.deployment').evaluate('swatch => getComputedStyle(swatch).backgroundColor')
        assertThat(page.locator('.by-department .highcharts-xaxis-labels').first().locator('text')).hasCount(5)
        assertThat(page.locator('.by-department dso-chart')).hasAttribute('aria-label',
                'AI Lab: none; Capital Partners: none; Corporate Technology: 3 passed; Custody: none; ' +
                        'Fund Services: 2 passed with warnings, 3 passed, 1 key invalidated')
        assertThat(page.locator('.by-department .legend span')).hasText(['Failed', 'Passed with warnings', 'Stopped', 'Not built',
                                                                        'Passed', 'No runs yet', 'Key invalidated'] as String[])
        api.lastRequest('GET', '/api/monitoring/activity').params() == [range: '30d']
        ownErrors().isEmpty()
    }

    def "a product's pipelines link their Jenkins jobs and the builds their runs recorded"() {
        given:
        def monitoring = fixture('monitoring-product-2.json') as Map
        def health = monitoring.pipelines as List<Map>
        health.find { it.pipeline.serviceName == 'notifications' }.lastRun.buildUrl = null
        api.respond('GET', '/api/monitoring/products/2', monitoring)
        open('/monitoring/products/2')

        expect:
        assertThat(gridCells(page.locator('body'), 'service')).hasText(health*.pipeline*.serviceName as String[])
        health.every { item ->
            def row = row(item.pipeline.id as int)
            assertThat(row.getByRole(LINK, new Locator.GetByRoleOptions().setName("Open the Jenkins job of ${item.pipeline.serviceName}")))
                    .hasAttribute('href', item.pipeline.jenkinsJobUrl as String)
            if (item.lastRun.buildUrl) {
                assertThat(row.locator('a.build-link')).hasAttribute('href', item.lastRun.buildUrl as String)
                assertThat(row.locator('a.build-link')).hasText("#${item.lastRun.build}")
            } else {
                assertThat(row.locator('a.build-link')).hasCount(0)
                assertThat(row).containsText("#${item.lastRun.build}")
            }
            true
        }
        !health.any { it.lastRun.buildUrl?.startsWith(it.pipeline.jenkinsJobUrl as String) }

        when:
        def popup = page.waitForPopup { row(7).getByRole(LINK, new Locator.GetByRoleOptions().setName('Open the Jenkins job of ledger')).click() }

        then:
        popup.url() == 'https://jenkins.bbh.com/job/DevSecOps/job/PAYHUB/job/ledger-full/'
        page.url().endsWith('/monitoring/products/2')

        when:
        popup.close()
        gridCell(row(7), 'service').click()
        page.waitForURL('**/monitoring/pipelines/7')

        then:
        assertThat(page.locator('h1')).containsText('ledger')
        ownErrors().isEmpty()
    }

    def "the pipeline page asks for the selected range and keeps it in the address"() {
        given:
        api.get('/api/monitoring/pipelines/1') { RecordedRequest request ->
            def monitoring = fixture('monitoring-pipeline-1.json') as Map
            monitoring.dora.rangeDays = (request.params().range - 'd') as int
            json(monitoring)
        }
        open('/monitoring/pipelines/1')

        expect:
        assertThat(period('30 days')).hasAttribute('aria-checked', 'true')
        assertThat(recentRunsNote()).hasText('Newest first, within the last 30 days')

        when:
        ['7d', '90d', '180d'].each { range ->
            period("${range - 'd'} days").click()
            page.waitForURL("**/monitoring/pipelines/1?range=$range")
            assertThat(recentRunsNote()).hasText("Newest first, within the last ${range - 'd'} days")
        }
        assertThat(page.locator('dso-loading.loading')).hasCount(0)
        button('Refresh the pipeline metrics', true).click()

        then:
        assertThat(period('180 days')).hasAttribute('aria-checked', 'true')
        awaitRequest('GET', '/api/monitoring/pipelines/1', 5).params() == [range: '180d']
        api.requests('GET', '/api/monitoring/pipelines/1')*.params()*.range == ['30d', '7d', '90d', '180d', '180d']

        when:
        open('/monitoring/pipelines/1?range=90d')

        then:
        api.lastRequest('GET', '/api/monitoring/pipelines/1').params() == [range: '90d']
        assertThat(period('90 days')).hasAttribute('aria-checked', 'true')

        when:
        open('/monitoring/pipelines/1?range=1y')

        then:
        api.lastRequest('GET', '/api/monitoring/pipelines/1').params() == [range: '30d']
        ownErrors().isEmpty()
    }

    def "the pipeline page links Jenkins, every Grafana instance and every build its runs recorded"() {
        given:
        def monitoring = fixture('monitoring-pipeline-1.json') as Map
        def dashboards = monitoring.grafana as List<Map>
        dashboards << [name: 'Grafana prod', dashboardUrl: 'https://grafana-prod.bbh.com/d/adzfc54123/devsecops-pipeline-long?var-project=CertScanner&from=now-30d&to=now']
        def runs = monitoring.recentRuns as List<Map>
        runs[1].buildUrl = 'https://jenkins.bbh.com/job/CERTSCANNER-gui/job/full/job/feature%252Flogin/60/'
        runs[2].buildUrl = null
        api.respond('GET', '/api/monitoring/pipelines/1', monitoring)
        open('/monitoring/pipelines/1')

        expect:
        assertThat(link('Open in Jenkins', true)).hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')
        assertThat(link('Manage pipeline', true)).hasAttribute('href', '/pipelines/1')
        assertThat(link('Open in Grafana', true)).hasAttribute('href', dashboards[0].dashboardUrl as String)
        assertThat(link('Open in Grafana prod', true)).hasAttribute('href', dashboards[1].dashboardUrl as String)
        assertThat(page.locator('.last-run a.build-link')).hasAttribute('href', monitoring.lastRun.buildUrl as String)
        def links = gridRows(recentRuns())
        assertThat(links).hasCount(runs.size())
        runs.withIndex().every { run, index ->
            def cell = gridCell(links.nth(index), 'build')
            if (run.buildUrl) {
                assertThat(cell.locator('a.build-link')).hasAttribute('href', run.buildUrl as String)
            } else {
                assertThat(cell.locator('a')).hasCount(0)
                assertThat(cell).hasText("#${run.build}")
            }
            true
        }
        assertThat(page.locator('.grafana h2')).hasText(['Grafana', 'Grafana prod'] as String[])
        assertThat(page.locator('.grafana iframe').first()).hasAttribute('src', "${dashboards[0].dashboardUrl}&kiosk".toString())
        assertThat(page.locator('.grafana iframe').last()).hasAttribute('src', "${dashboards[1].dashboardUrl}&kiosk".toString())
        ownErrors().isEmpty()
    }

    def "a pipeline whose key is invalidated says so on its monitoring page"() {
        when:
        open('/monitoring/pipelines/9')

        then:
        assertThat(page.locator('.banner').first()).containsText('The key of this pipeline is invalidated, so the pipeline is refused its settings and stops until a new key is issued')
        assertThat(page.locator('.page-header dso-status-chip')).containsText('Key invalidated')
        ownErrors().isEmpty()
    }

    def "without InfluxDB the pages explain that only the key state is known"() {
        given:
        api.respond('GET', '/api/monitoring/status', [influxConfigured: false, influxReachable: false, influxError: null])
        def overview = fixture('monitoring-products.json') as Map
        overview.products.each { product -> product.overall = 'NO_DATA'; product.statusCounts = [NO_DATA: product.pipelineCount]; product.lastRunAt = null }
        api.respond('GET', '/api/monitoring/products', overview)
        def activity = fixture('monitoring-activity.json') as Map
        activity.dora = (activity.dora as Map) + [runs: 0, deployments: 0, daily: []]
        activity.metricsError = 'InfluxDB is not configured for the portal'
        api.respond('GET', '/api/monitoring/activity', activity)
        def pipeline = fixture('monitoring-pipeline-1.json') as Map
        pipeline += [status: 'NO_DATA', lastRun: null, recentRuns: [], grafana: [],
                     dora  : (pipeline.dora as Map) + [runs: 0, deployments: 0, daily: []]]
        api.respond('GET', '/api/monitoring/pipelines/1', pipeline)

        when:
        open('/monitoring')

        then:
        assertThat(page.locator('dso-metrics-banner .banner.info')).containsText('Run results are not shown: the portal is not connected to InfluxDB')
        assertThat(page.locator('dso-metrics-banner .banner.info .admin')).hasText('For the administrator: set INFLUX_URL and INFLUX_TOKEN.')
        assertThat(stat('Pipelines')).hasText('9')
        assertThat(stat('Passed')).hasText('0')
        assertThat(page.locator('a.product .last-run')).hasText(['No runs yet', 'No runs yet'] as String[])
        assertThat(page.locator('dso-dora-tiles')).hasCount(0)
        assertThat(page.locator('.portfolio')).hasCount(0)
        assertThat(page.locator('.dora-unavailable')).hasCount(0)

        when:
        open('/monitoring/pipelines/1')

        then:
        assertThat(page.locator('.last-run')).containsText('No run reported yet. Runs appear here once the pipeline has run and reported them.')
        assertThat(page.locator('.grafana .missing')).containsText('No Grafana dashboard is linked to the portal')
        assertThat(page.locator('.grafana .missing .admin')).hasText('For the administrator: set GRAFANA_DASHBOARD_URL, and GRAFANA_2_DASHBOARD_URL for a second Grafana.')
        assertThat(page.locator('iframe')).hasCount(0)
        ownErrors().isEmpty()
    }

    def "an unreachable InfluxDB and failed metric reads are named"() {
        given:
        api.respond('GET', '/api/monitoring/status', [influxConfigured: true, influxReachable: false, influxError: 'connection refused'])
        def overview = fixture('monitoring-products.json') as Map
        overview.metricsError = 'query timed out after 10 seconds'
        api.respond('GET', '/api/monitoring/products', overview)
        def activity = fixture('monitoring-activity.json') as Map
        activity.dora = (activity.dora as Map) + [runs: 0, deployments: 0, daily: []]
        activity.metricsError = 'query timed out after 20 seconds'
        api.respond('GET', '/api/monitoring/activity', activity)

        when:
        open('/monitoring')

        then:
        assertThat(page.locator('dso-metrics-banner .banner')).hasText([
                'Run results could not be loaded: InfluxDB, where the pipelines report their runs, does not answer (connection refused). ' +
                        'Try again in a moment; if it keeps failing, tell the portal administrator.',
                'Run results could not be loaded, so the statuses below may be incomplete (query timed out after 10 seconds). ' +
                        'Try again in a moment; if it keeps failing, tell the portal administrator.'] as String[])
        assertThat(page.locator('.dora-unavailable h2')).hasText('Delivery performance (DORA)')
        assertThat(page.locator('.dora-unavailable .banner span').first()).hasText(
                'Delivery performance and runs per day could not be loaded. The run results could not be read (query timed out after 20 seconds). ' +
                        'Try again in a moment; if it keeps failing, tell the portal administrator.')
        assertThat(page.locator('dso-dora-tiles')).hasCount(0)
        ownErrors().isEmpty()
    }

    def "a failed read of the delivery performance is named and loaded again on request"() {
        given:
        api.respond('GET', '/api/monitoring/activity', problem(500, 'Internal Server Error', 'The runs of the last 30 days could not be summed up'))
        open('/monitoring')

        expect:
        assertThat(page.locator('.dora-unavailable .banner span').first())
                .hasText('Delivery performance and runs per day could not be loaded. The runs of the last 30 days could not be summed up')
        assertThat(page.locator('dso-dora-tiles')).hasCount(0)

        when:
        api.respond('GET', '/api/monitoring/activity', fixture('monitoring-activity.json'))
        buttonIn(page.locator('.dora-unavailable'), 'Try again').click()

        then:
        assertThat(page.locator('dso-dora-tiles .tile-value')).hasText(['1.4 / day', '41h 33m', '29.0%', '13h 24m'] as String[])
        assertThat(page.locator('.dora-unavailable')).hasCount(0)
        api.requests('GET', '/api/monitoring/activity').size() == 2
        ownErrors().findAll { !it.contains('500') }.isEmpty()
    }

    def "Refresh loads the departments again, so a failed read of them clears"() {
        given:
        api.respond('GET', '/api/departments', problem(503, 'Service Unavailable', 'The database is not available'))
        open('/monitoring')

        expect:
        assertThat(page.locator('.products .banner span').first()).hasText('The products could not be loaded. The database is not available')
        assertThat(productCards()).hasCount(0)

        when:
        api.respond('GET', '/api/departments', fixture('departments.json'))
        button('Refresh', true).click()

        then:
        assertThat(page.locator('.department-title h3')).hasText(['Corporate Technology', 'Fund Services'] as String[])
        assertThat(page.locator('.products .banner')).hasCount(0)
        api.requests('GET', '/api/departments').size() == 2
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    Locator productCards() {
        page.locator('a.product .names strong')
    }

    Locator period(String label) {
        radio(page.getByRole(RADIOGROUP, new Page.GetByRoleOptions().setName('Period')), label)
    }

    Locator filter() {
        page.getByLabel('Filter products')
    }

    Locator row(int pipelineId) {
        holding(gridRows(), "a.pipeline-link[href='/monitoring/pipelines/${pipelineId}']")
    }

    Locator recentRuns() {
        holdingText(page.locator('section.card'), 'Recent runs')
    }

    Locator recentRunsNote() {
        recentRuns().locator('.card-header .muted')
    }
}
