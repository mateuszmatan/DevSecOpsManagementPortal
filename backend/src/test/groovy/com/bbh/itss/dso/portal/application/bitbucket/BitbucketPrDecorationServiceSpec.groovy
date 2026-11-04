package com.bbh.itss.dso.portal.application.bitbucket

import com.bbh.itss.dso.portal.application.bitbucket.port.out.BitbucketPullRequestPort
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationRequest
import com.bbh.itss.dso.portal.domain.bitbucket.PrFinding
import spock.lang.Specification

class BitbucketPrDecorationServiceSpec extends Specification {

    BitbucketPullRequestPort port = Mock()
    BitbucketPrDecorationService service = new BitbucketPrDecorationService(port)

    def "decorates pull request with inline line comments and general summary"() {
        given:
        def findings = [
                new PrFinding('SAST', 'HIGH', 'src/main/App.java', 42, 'SQL Injection', 'Unsanitized parameter used in query', 'Use PreparedStatement'),
                new PrFinding('SONAR', 'MAJOR', 'src/main/App.java', 15, 'Code smell', 'Cognitive complexity too high', 'Refactor into smaller methods'),
                new PrFinding('DAST', 'HIGH', null, null, 'CORS Misconfiguration', 'Wildcard CORS allowed', 'Restrict origin')
        ]
        def req = new PrDecorationRequest('https://bitbucket.bbh.com/projects/TA/repos/cert-scanner', '12', 'cred-id', 'feat', 'main', findings, 'Static security summary')

        when:
        def result = service.decorate(req)

        then:
        1 * port.addLineComment('https://bitbucket.bbh.com/projects/TA/repos/cert-scanner', '12', 'cred-id', 'src/main/App.java', 42, { String s -> s.contains('SQL Injection') && s.contains('Use PreparedStatement') })
        1 * port.addLineComment('https://bitbucket.bbh.com/projects/TA/repos/cert-scanner', '12', 'cred-id', 'src/main/App.java', 15, { String s -> s.contains('Cognitive complexity') })
        1 * port.addComment('https://bitbucket.bbh.com/projects/TA/repos/cert-scanner', '12', 'cred-id', 'Static security summary')
        result.status == 'SUCCESS'
        result.lineCommentsAdded == 2
        result.commentsAdded == 1
    }
}
