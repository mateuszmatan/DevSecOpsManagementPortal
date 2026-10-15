package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.support.FakeInfluxDb
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InfluxQueryClientSpec extends Specification {

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
        def rows = client(influx.url, token).query('buckets() |> limit(n: 1)')

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
        client.query('buckets()')

        then:
        thrown(HttpClientErrorException.Unauthorized)

        when:
        client.read { client.query('buckets()') }

        then:
        def e = thrown(UncheckedIOException)
        e.message.startsWith('InfluxDB could not be read: 401 Unauthorized')
    }

    def "without a URL '#url' nothing is queried or read and the reason is given"() {
        given:
        def client = client(url)
        def reads = 0

        when:
        client.query('buckets()')

        then:
        !client.configured()
        !new InfluxProperties(url, 'DevSecOps', 'DORA-metrics', null, '365d').configured()
        thrown(UncheckedIOException)

        when:
        client.read { reads++ }

        then:
        reads == 0
        def e = thrown(UncheckedIOException)
        e.message == 'InfluxDB is not configured for the portal'

        when:
        client.requireConfigured()

        then:
        thrown(UncheckedIOException)

        where:
        url << [null, ' ']
    }

    def "a reading of a configured client gives what was read, and a failed one the reason, cut to 300 characters"() {
        given:
        def client = client(influx.url)

        expect:
        client.configured()
        client.read { 'read' } == 'read'
        client.bucket() == '"DORA-metrics"'
        client.lastRunLookback() == '365d'

        when:
        client.read { throw failure }

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

    private static InfluxQueryClient client(String url, String token = null) {
        new InfluxQueryClient(new InfluxProperties(url, 'DevSecOps', 'DORA-metrics', token, '365d'), RestClient.builder())
    }
}
