package com.bbh.itss.dso.portal.domain.monitoring

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

class MonitoringRangeSpec extends Specification {

    def "the range '#range' covers #days days"() {
        expect:
        MonitoringRange.parse(range).days() == days

        where:
        range   || days
        '7d'    || 7
        ' 90d ' || 90
        '730d'  || 730
    }

    def "the range '#range' is refused: #message"() {
        when:
        MonitoringRange.parse(range)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field == ['range']
        e.problems()*.message == [message]

        where:
        range    || message
        null     || 'use a number of days such as 7d, 30d or 90d'
        ''       || 'use a number of days such as 7d, 30d or 90d'
        '0d'     || 'use a number of days such as 7d, 30d or 90d'
        '30'     || 'use a number of days such as 7d, 30d or 90d'
        'd'      || 'use a number of days such as 7d, 30d or 90d'
        '30h'    || 'use a number of days such as 7d, 30d or 90d'
        '-1d'    || 'use a number of days such as 7d, 30d or 90d'
        '7d) |>' || 'use a number of days such as 7d, 30d or 90d'
        '731d'   || 'can cover at most 730 days'
        '1000d'  || 'use a number of days such as 7d, 30d or 90d'
    }

    def "a range of #days days cannot be made"() {
        when:
        new MonitoringRange(days)

        then:
        thrown(InvalidRequestException)

        where:
        days << [0, -5, 731]
    }
}
