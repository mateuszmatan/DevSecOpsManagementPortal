package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.LOCAL
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE
import static com.bbh.itss.dso.portal.domain.catalog.TestSettings.DEFAULTS
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.PERFORMANCE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.REGRESSION
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings.NONE
import static com.bbh.itss.dso.portal.domain.shared.Sections.messages
import static com.bbh.itss.dso.portal.domain.shared.Sections.problems
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class TestSectionsSpec extends Specification {

    static final String REMOTE_URL = 'https://jenkins-qc.bbh.com/job/CERT/job/regression/'

    def "unit test settings trim their paths and allow no empty results by default"() {
        expect:
        new UnitTestSettings(null, ' **/TEST-*.xml ', ' ', ' gui/build ', null, ' ') ==
                new UnitTestSettings(ToolCommand.NONE, '**/TEST-*.xml', null, 'gui/build', false, null)
        NONE == new UnitTestSettings(ToolCommand.NONE, ' ', '', ' ', false, ' ')
    }

    def "a Gradle unit tests stage writes its command, its reports and the coverage report path"() {
        given:
        def testCommand = ToolCommand.builder().tasks(['test', 'jacocoTestReport']).flags(['--continue'])
                .directory('gui').environment(['CI=true']).build()
        def unitTests = new UnitTestSettings(testCommand, '**/TEST-*.xml', 'gui', 'build/reports', true,
                'build/reports/jacoco/test/jacocoTestReport.xml')

        expect:
        written { unitTests.writeTo(it, GRADLE) } ==
                [tests   : [unitTests: [gradle           : [tasks: ['test', 'jacocoTestReport'], flags: ['--continue'], dir: 'gui',
                                                            env  : [CI: 'true']],
                                        unitTestResult   : '**/TEST-*.xml', rootDir: 'gui', reportOutDir: 'build/reports',
                                        allowEmptyResults: true]],
                 coverage: [reportPath: 'build/reports/jacoco/test/jacocoTestReport.xml']]
    }

    def "a #tool unit tests stage writes #config"() {
        expect:
        written { unitTests.writeTo(it, tool) } == config

        where:
        tool << [MAVEN, FLUTTER, GRADLE, MAVEN, FLUTTER]
        unitTests << [UnitTestSettings.builder()
                              .command(ToolCommand.builder().tasks(['test', 'jacoco:report']).mavenHome('/opt/maven').build())
                              .coverageReportPath('target/site/jacoco/jacoco.xml').build(),
                      UnitTestSettings.builder().command(ToolCommand.of(['test'], []))
                              .resultPattern('test-results/*.xml').build(),
                      NONE, NONE, NONE]
        config << [[tests   : [unitTests: [maven: [goals: ['test', 'jacoco:report'], mvnPath: '/opt/maven']]],
                    coverage: [reportPath: 'target/site/jacoco/jacoco.xml']],
                   [tests: [unitTests: [unitTestResult: 'test-results/*.xml']]], [:], [:], [:]]
    }

    def "a #tool unit tests stage that sets #description reports #fields"() {
        expect:
        problems { unitTests.validate(it, tool) } == fields

        where:
        description                | tool    | unitTests                                                                                       || fields
        'nothing'                  | GRADLE  | NONE                                                                                            || []
        'only a coverage report'   | MAVEN   | UnitTestSettings.builder().coverageReportPath('jacoco.xml').build()                             || []
        'a result pattern'         | GRADLE  | UnitTestSettings.builder().resultPattern('**/TEST-*.xml').build()                               || ['command.tasks']
        'a root directory'         | MAVEN   | UnitTestSettings.builder().rootDir('gui').build()                                               || ['command.tasks']
        'a report directory'       | GRADLE  | UnitTestSettings.builder().reportOutDir('reports').build()                                      || ['command.tasks']
        'empty results allowed'    | MAVEN   | UnitTestSettings.builder().allowEmptyResults(true).build()                                      || ['command.tasks']
        'flags without tasks'      | GRADLE  | UnitTestSettings.builder().command(ToolCommand.of([], ['--info'])).build()                      || ['command.tasks']
        'a step label'             | MAVEN   | UnitTestSettings.builder().command(ToolCommand.builder().label('Tests').build()).build()         || ['command.tasks']
        'tasks'                    | GRADLE  | UnitTestSettings.builder().command(ToolCommand.of(['test'], [])).resultPattern('**/*.xml').build() || []
        'a result pattern'         | FLUTTER | UnitTestSettings.builder().resultPattern('**/TEST-*.xml').allowEmptyResults(true).build()       || []
    }

    def "a unit tests stage without a command is explained in the words of the build tool"() {
        given:
        def unitTests = UnitTestSettings.builder().resultPattern('**/TEST-*.xml').build()

        expect:
        messages { unitTests.validate(it, MAVEN) } == ['add the Maven goals of the unit tests, for example test jacoco:report']
        messages { unitTests.validate(it, GRADLE) } == ['add the Gradle tasks of the unit tests, for example test jacocoTestReport']
    }

    def "a test job trims its values and stores blank ones as null"() {
        expect:
        TestJob.builder().stage(SMOKE).name(' ').job(' CERT/gui-smoke ').parameters(' ').remoteJenkins(' ')
                .remoteJenkinsUrl(' ').credentialsId(' ').build() ==
                TestJob.of(SMOKE, null, null, 'CERT/gui-smoke', null)
        TestJob.of(SMOKE, null, null, null, null).job() == null
    }

    def "a job given as #job is a URL: #url"() {
        expect:
        TestJob.of(SMOKE, null, null, job, null).isUrl() == url

        where:
        job                                   || url
        'https://jenkins-qc.bbh.com/job/x/'   || true
        ' http://jenkins-qc:8080/job/x '      || true
        'CERT/gui-smoke'                      || false
        'folder/https-check'                  || false
        null                                  || false
    }

    def "a #type job given as #job with remote Jenkins #remote and URL #remoteUrl must name its Jenkins: #needed"() {
        expect:
        TestJob.builder().stage(SMOKE).type(type).job(job).remoteJenkins(remote).remoteJenkinsUrl(remoteUrl).build()
                .needsRemoteJenkins() == needed

        where:
        type   | job                          | remote | remoteUrl            || needed
        REMOTE | 'CERT/smoke'                 | null   | null                 || true
        REMOTE | 'https://jenkins.qc/job/s/'  | null   | null                 || false
        REMOTE | 'CERT/smoke'                 | 'qc'   | null                 || false
        REMOTE | 'CERT/smoke'                 | null   | 'https://jenkins.qc' || false
        LOCAL  | 'CERT/smoke'                 | null   | null                 || false
        null   | 'CERT/smoke'                 | null   | null                 || false
    }

    def "a job URL is written as url, a path as job, with only the fields that are set in the library's order"() {
        when:
        def entry = TestJob.builder().stage(REGRESSION).name('regression').type(REMOTE).job(REMOTE_URL)
                .timeoutMinutes(90).parameters('ENV=qc\nBROWSER=chrome').remoteJenkins('jenkins-qc')
                .remoteJenkinsUrl('https://jenkins-qc.bbh.com').credentialsId('jenkins-qc-token').build().toConfig()

        then:
        entry == [name            : 'regression', type: 'remote', url: REMOTE_URL, timeoutMin: 90,
                  parameters      : 'ENV=qc\nBROWSER=chrome', remoteJenkins: 'jenkins-qc',
                  remoteJenkinsUrl: 'https://jenkins-qc.bbh.com', credentialsId: 'jenkins-qc-token']
        entry.keySet() as List == ['name', 'type', 'url', 'timeoutMin', 'parameters', 'remoteJenkins', 'remoteJenkinsUrl',
                                   'credentialsId']
        TestJob.of(SMOKE, null, LOCAL, 'CERT/smoke', 15).toConfig() ==
                [type: 'local', job: 'CERT/smoke', timeoutMin: 15]
        TestJob.of(SMOKE, null, null, 'CERT/gui-smoke', null).toConfig() == [job: 'CERT/gui-smoke']
    }

    def "each stage writes its own limit and its jobs in the order they were entered"() {
        given:
        def smoke1 = TestJob.of(SMOKE, 'smoke-api', null, 'CERT/api-smoke', null)
        def regression = TestJob.of(REGRESSION, null, REMOTE, REMOTE_URL, 60)
        def smoke2 = TestJob.of(SMOKE, 'smoke-gui', null, 'CERT/gui-smoke', 10)

        when:
        def tests = written {
            TestSettings.builder().maxParallel(4).smokeMaxParallel(2).performanceMaxParallel(1).build()
                    .writeTo(it, [smoke1, regression, smoke2])
        }.tests

        then:
        tests == [maxParallel: 4,
                  smoke      : [maxParallel: 2, jobs: [[name: 'smoke-api', job: 'CERT/api-smoke'],
                                                       [name: 'smoke-gui', job: 'CERT/gui-smoke', timeoutMin: 10]]],
                  regression : [jobs: [[type: 'remote', url: REMOTE_URL, timeoutMin: 60]]],
                  performance: [maxParallel: 1]]
        tests.keySet() as List == ['maxParallel', 'smoke', 'regression', 'performance']
    }

    def "a stage limit is written even when the stage has no jobs, and the defaults without jobs write nothing"() {
        expect:
        TestStage.values()*.configKey() == ['smoke', 'regression', 'performance']
        DEFAULTS == new TestSettings(null, null, null, null, true, true, true, null, null, null)
        written { DEFAULTS.writeTo(it, []) } == [:]
        written { TestSettings.builder().regressionMaxParallel(3).build().writeTo(it, []) } ==
                [tests: [regression: [maxParallel: 3]]]
        written { TestSettings.builder().performanceMaxParallel(5).build().writeTo(it, []) } ==
                [tests: [performance: [maxParallel: 5]]]
        written { DEFAULTS.writeTo(it, [TestJob.of(PERFORMANCE, null, null, 'CERT/load', null)]) } ==
                [tests: [performance: [jobs: [[job: 'CERT/load']]]]]
    }

    def "a remote job writes its poll interval, its token credential and the trigger options that are on"() {
        when:
        def entry = TestJob.builder().stage(SMOKE).type(REMOTE).job(REMOTE_URL).pollIntervalSec(30)
                .tokenCredentialsId(' trigger-token ').abortTriggeredJob(true).overrideTrustAllCertificates(true)
                .trustAllCertificates(true).useJobInfoCache(true).build().toConfig()

        then:
        entry == [type: 'remote', url: REMOTE_URL, pollIntervalSec: 30, tokenCredentialsId: 'trigger-token',
                  abortTriggeredJob: true, overrideTrustAllCertificates: true, trustAllCertificates: true,
                  useJobInfoCache: true]
    }

    def "a stage that is not required is written as required false even without jobs, with its poll interval"() {
        expect:
        new TestSettings(null, null, null, null, null, null, null, null, null, null) == DEFAULTS
        written {
            TestSettings.builder().smokeRequired(false).regressionRequired(true).smokePollIntervalSec(20)
                    .performancePollIntervalSec(45).build().writeTo(it, [])
        } ==
                [tests: [smoke: [required: false, pollIntervalSec: 20], performance: [pollIntervalSec: 45]]]
    }
}
