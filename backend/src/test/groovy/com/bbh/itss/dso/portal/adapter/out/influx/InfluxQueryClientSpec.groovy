package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.domain.settings.PlatformSettings
import com.bbh.itss.dso.portal.support.FakeInfluxDb
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InfluxQueryClientSpec extends Specification {

    static final PlatformSettings UNSET = PlatformSettings.builder().build()

    @Shared
    @AutoCleanup
    FakeInfluxDb influx = FakeInfluxDb.start()

    def cleanup() {
        influx.reset()
    }

    def "a Flux query is posted as JSON with the token and the CSV comes back as rows"() {
        given:
        influx.respondWith(',result,table,name\r\n,_result,0,DORA-metrics\r\n')

        when:
        def rows = client(influx.url, token).query(UNSET, 'buckets() |> limit(n: 1)')

        then:
        rows == [[table: '0', name: 'DORA-metrics']]
        with(influx.requests.first()) {
            method == 'POST'
            path == '/api/v2/query'
            query == 'org=DevSecOps'
            authorization.trim() == "Token ${token ?: ''}".trim()
            contentType.startsWith('application/json')
            accept == 'application/csv'
            body == [query: 'buckets() |> limit(n: 1)', type: 'flux', dialect: [header: true, annotations: [], delimiter: ',']]
        }

        where:
        token << ['read-token', null]
    }

    def "an error from InfluxDB fails the query, and a reading through the client gives its status"() {
        given:
        influx.failWith(401)
        def client = client(influx.url, 'wrong')

        when:
        client.query(UNSET, 'buckets()')

        then:
        thrown(HttpClientErrorException.Unauthorized)

        when:
        client.read(UNSET) { client.query(UNSET, 'buckets()') }

        then:
        def e = thrown(UncheckedIOException)
        e.message.startsWith('InfluxDB could not be read: 401 Unauthorized')
    }

    def "without a URL '#url' nothing is queried or read and the reason is given"() {
        given:
        def client = client(url)
        def reads = 0

        when:
        client.query(UNSET, 'buckets()')

        then:
        !client.configured(UNSET)
        !new InfluxProperties(url, 'DevSecOps', 'DORA-metrics', null, '365d').configured()
        thrown(UncheckedIOException)

        when:
        client.read(UNSET) { reads++ }

        then:
        reads == 0
        def e = thrown(UncheckedIOException)
        e.message == 'InfluxDB is not configured for the portal'

        when:
        client.requireConfigured(UNSET)

        then:
        thrown(UncheckedIOException)

        where:
        url << [null, ' ']
    }

    def "a reading of a configured client gives what was read, and a failed one the reason, cut to 300 characters"() {
        given:
        def client = client(influx.url)

        expect:
        client.configured(UNSET)
        client.read(UNSET) { 'read' } == 'read'
        client.bucket(UNSET) == '"DORA-metrics"'
        client.lastRunLookback() == '365d'

        when:
        client.read(UNSET) { throw failure }

        then:
        def e = thrown(UncheckedIOException)
        e.message == error

        where:
        failure                                             || error
        new IllegalStateException('401 Unauthorized')       || 'InfluxDB could not be read: 401 Unauthorized'
        new IllegalStateException('x' * 300)                || 'InfluxDB could not be read: ' + 'x' * 300
        new IllegalStateException('0123456789' * 40)        || 'InfluxDB could not be read: ' + ('0123456789' * 30)
        new RuntimeException()                              || 'InfluxDB could not be read: RuntimeException'
        new UncheckedIOException('kept', new IOException()) || 'kept'
    }

    def "the look-back for the latest runs must be a Flux duration"() {
        when:
        new InfluxProperties('http://localhost:8086', 'DevSecOps', 'DORA-metrics', null, lookback)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "'$lookback' is not a Flux duration such as 365d"

        where:
        lookback << [null, '', '365', '-1d', '1d) |> drop()', '0d']
    }

    def "settings saved in the management panel override the environment properties"() {
        given:
        influx.respondWith(',result,table,name\r\n,_result,0,custom-bucket\r\n')
        def client = client(null)
        def panel = PlatformSettings.builder()
                .influxUrl(influx.url)
                .influxOrg('CustomOrg')
                .influxBucket('custom-bucket')
                .influxToken('panel-token')
                .build()

        when:
        def rows = client.query(panel, 'buckets() |> limit(n: 1)')

        then:
        !client.configured(UNSET)
        client.configured(panel)
        client.bucket(panel) == '"custom-bucket"'
        rows == [[table: '0', name: 'custom-bucket']]
        with(influx.requests.first()) {
            query == 'org=CustomOrg'
            authorization == 'Token panel-token'
        }
    }

    def "the token of the environment goes only to the InfluxDB of the environment: #portal"() {
        given:
        influx.respondWith(',result,table,name\r\n,_result,0,DORA-metrics\r\n')
        def client = client(environmentUrl, 'secret-token')

        when:
        client.query(PlatformSettings.builder().influxUrl(savedUrl).build(), 'buckets() |> limit(n: 1)')

        then:
        influx.requests.first().authorization.trim() == expected

        where:
        portal                              | environmentUrl          | savedUrl                || expected
        'no URL saved in the portal'        | influx.url              | null                    || 'Token secret-token'
        'the same URL saved in the portal'  | influx.url              | influx.url              || 'Token secret-token'
        'another URL saved in the portal'   | 'https://influx.bbh.com' | influx.url             || 'Token'
    }

    private static InfluxQueryClient client(String url, String token = null) {
        new InfluxQueryClient(new InfluxProperties(url, 'DevSecOps', 'DORA-metrics', token, '365d'), RestClient.builder())
    }
}

