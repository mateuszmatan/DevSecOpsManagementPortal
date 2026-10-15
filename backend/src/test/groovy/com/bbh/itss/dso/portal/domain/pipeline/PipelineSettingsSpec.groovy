package com.bbh.itss.dso.portal.domain.pipeline

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings.jobUrl

class PipelineSettingsSpec extends Specification {

    static final String JENKINS = 'https://jenkins.test'

    def both = new PipelineSettings(['linux'], 'CERT/gui-extended', 'CERT/gui-security', 'DevSecOps/CERT/gui', 'Nightly')

    def "blank settings are stored as null and agent labels cleaned"() {
        expect:
        new PipelineSettings([' linux ', '', 'linux', 'docker'], ' ', '  ', ' DevSecOps/gui ', '\t') ==
                new PipelineSettings(['linux', 'docker'], null, null, 'DevSecOps/gui', null)
        new PipelineSettings(null, null, null, null, null).agentLabels() == []
    }

    def "agent labels #labels are refused with #problem"() {
        given:
        def problems = new ValidationProblems()

        when:
        new PipelineSettings(labels, null, null, null, null).validate(problems)

        then:
        problems.list()*.message == problem

        where:
        labels           || problem
        ['  ', null]     || ['add at least one Jenkins agent label']
        null             || ['add at least one Jenkins agent label']
        ['linux-agent']  || []
    }

    def "the job #job under Jenkins #jenkinsUrl is linked as #url"() {
        expect:
        jobUrl(job, jenkinsUrl) == url

        where:
        job                                         | jenkinsUrl                || url
        null                                        | JENKINS                   || null
        '   '                                       | JENKINS                   || null
        'https://jenkins.bbh.com/job/CERT/job/gui/' | null                      || 'https://jenkins.bbh.com/job/CERT/job/gui/'
        ' http://jenkins.bbh.com/job/gui '          | JENKINS                   || 'http://jenkins.bbh.com/job/gui'
        'DevSecOps/TARA/app-full'                   | null                      || null
        'DevSecOps/TARA/app-full'                   | '  '                      || null
        'DevSecOps/TARA/app-full'                   | JENKINS                   || 'https://jenkins.test/job/DevSecOps/job/TARA/job/app-full/'
        'app-full'                                  | 'https://jenkins.test/ci' || 'https://jenkins.test/ci/job/app-full/'
        ' /DevSecOps//TARA/ '                       | 'https://jenkins.test///' || 'https://jenkins.test/job/DevSecOps/job/TARA/'
        'CERT Scanner/ gui#1 '                      | JENKINS                   || 'https://jenkins.test/job/CERT%20Scanner/job/gui%231/'
        'Zażółć/gęślą'                              | JENKINS                   || 'https://jenkins.test/job/Za%C5%BC%C3%B3%C5%82%C4%87/job/g%C4%99%C5%9Bl%C4%85/'
    }

    def "a pipeline's own job is linked under the Jenkins URL"() {
        expect:
        both.jenkinsJobUrl(JENKINS) == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui/'
        both.jenkinsJobUrl(null) == null
        new PipelineSettings(['linux'], null, null, null, null).jenkinsJobUrl(JENKINS) == null
        new PipelineSettings(['linux'], null, null, 'https://jenkins.bbh.com/job/x/', null).jenkinsJobUrl(null) ==
                'https://jenkins.bbh.com/job/x/'
    }

    def "a run that recorded the job #recorded was built by the job #job: #builds"() {
        expect:
        new PipelineSettings(['linux'], null, null, job, null).builds(recorded) == builds

        where:
        job                                                                    | recorded                         || builds
        'DevSecOps/CertScanner-pipeline'                                       | 'DevSecOps/CertScanner-pipeline' || true
        ' /DevSecOps/ CertScanner '                                            | 'DevSecOps/CertScanner/develop'  || true
        'DevSecOps/CertScanner'                                                | 'DevSecOps/CertScanner-pipeline' || false
        'https://jenkins.bbh.com/job/DevSecOps/job/CERT%20Scanner/'            | 'DevSecOps/CERT Scanner'         || true
        'https://jenkins.bbh.com/ci/job/DevSecOps/job/gui/job/feature%252Fx/?x' | 'DevSecOps/gui/feature%2Fx'      || true
        'https://jenkins.bbh.com/job/job/job/a+b%ZZ/'                          | 'job/a+b%ZZ'                     || true
        'https://jenkins.bbh.com/'                                             | 'DevSecOps/gui'                  || false
        null                                                                   | 'DevSecOps/gui'                  || false
        'DevSecOps/gui'                                                        | null                             || false
    }
}
