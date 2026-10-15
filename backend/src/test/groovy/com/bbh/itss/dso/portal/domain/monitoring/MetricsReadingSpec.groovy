package com.bbh.itss.dso.portal.domain.monitoring

import spock.lang.Specification

class MetricsReadingSpec extends Specification {

    def "a reading holds what the query returned, or the fallback and the reason when the metrics are unavailable"() {
        expect:
        MetricsReading.of({ -> [1, 2] }, []) == new MetricsReading([1, 2], null)
        !MetricsReading.of({ -> [1, 2] }, []).failed()
        MetricsReading.of({ -> throw new UncheckedIOException('InfluxDB is down', new IOException()) }, []) ==
                new MetricsReading([], 'InfluxDB is down')
        MetricsReading.of({ -> throw new UncheckedIOException('InfluxDB is down', new IOException()) }, []).failed()
    }

    def "other failures are not swallowed"() {
        when:
        MetricsReading.of({ -> throw new IllegalStateException('bug') }, [])

        then:
        thrown(IllegalStateException)
    }
}
