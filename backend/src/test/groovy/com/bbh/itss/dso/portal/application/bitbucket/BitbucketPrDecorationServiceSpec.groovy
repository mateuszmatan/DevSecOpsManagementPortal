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
        1 * port.addComment('https://bitbucket.bbh.com/projects/TA/repos/cert-scanner', '12', 'cred-id', { String s -> s.startsWith('**[DevSecOps] Findings without a file of this repository**') && s.contains('CORS Misconfiguration') && s.contains('**Recommended fix:** Restrict origin') })
        result.lineCommentsAdded == 2
        result.commentsAdded == 2
    }

    def "a finding in a file the pull request does not change is a pull request comment that names the file and the line"() {
        given:
        def url = 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner'
        def findings = [
                new PrFinding('AppScan SAST', 'HIGH', 'src/main/App.java', 42, 'SQL Injection', 'Found by HCL AppScan', 'Use PreparedStatement'),
                new PrFinding('AppScan SAST', 'HIGH', 'Dao.java', 9, 'SQL Injection', 'Found by HCL AppScan', 'Use PreparedStatement'),
                new PrFinding('AppScan SAST', 'CRITICAL', 'src/main/Other.java', 7, 'Command Injection', 'Found by HCL AppScan', 'Do not pass input to a shell'),
                new PrFinding('AppScan SAST', 'CRITICAL', 'src/main/Other.java', 19, 'Command Injection', 'Found by HCL AppScan', 'Do not pass input to a shell')
        ]
        port.changedFiles(url, '12', 'cred-id') >> ['src/main/App.java', 'src/main/db/Dao.java']

        when:
        def result = service.decorate(new PrDecorationRequest(url, '12', 'cred-id', 'feat', 'main', findings, null))

        then:
        1 * port.addLineComment(url, '12', 'cred-id', 'src/main/App.java', 42, { String s -> s.contains('**Recommended fix:** Use PreparedStatement') })
        1 * port.addLineComment(url, '12', 'cred-id', 'src/main/db/Dao.java', 9, _)
        1 * port.addComment(url, '12', 'cred-id', { String s ->
            s.startsWith('**[DevSecOps] Findings in `src/main/Other.java`** (this pull request does not change the file)') &&
                    s.contains('`src/main/Other.java:7`') && s.contains('`src/main/Other.java:19`') && s.contains('Do not pass input to a shell')
        })
        0 * port.addLineComment(url, '12', 'cred-id', 'src/main/Other.java', _, _)
        result.lineCommentsAdded == 2
        result.commentsAdded == 1
    }

    def "when the changed files are unknown every finding with a line is a line comment"() {
        given:
        def url = 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner'
        port.changedFiles(url, '12', 'cred-id') >> null

        when:
        def result = service.decorate(new PrDecorationRequest(url, '12', 'cred-id', 'feat', 'main',
                [new PrFinding('AppScan SAST', 'HIGH', 'src/main/Other.java', 7, 'SQL Injection', null, 'Use PreparedStatement')], null))

        then:
        1 * port.addLineComment(url, '12', 'cred-id', 'src/main/Other.java', 7, _)
        result.lineCommentsAdded == 1
        result.commentsAdded == 0
    }
}
