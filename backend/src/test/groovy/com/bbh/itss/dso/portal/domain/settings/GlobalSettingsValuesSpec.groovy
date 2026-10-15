package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy.INHERITED
import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA
import static com.bbh.itss.dso.portal.domain.settings.SeverityLimits.ZERO
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy

class GlobalSettingsValuesSpec extends Specification {

    static final GlobalSettingsValues BBH = bbhDefaults()

    def "the BBH defaults hold zero limits for every scanner and the DSOEnhanced policy"() {
        expect:
        BBH.limits() == [(SAST): ZERO, (SCA): ZERO, (NEXUS_IQ): ZERO, (DAST): ZERO]
        BBH.platform().jenkinsUrl() == null
        BBH.platform().jenkinsLibrary() == 'DevSecOpsJenkinsLibrary'
        BBH.deployment() == new DeploymentDefaults('deploy.bbh.com', 'tomcat-app-process', 'rdltaapps1.testbbh.com',
                'qcltaapps1.testbbh.com', 'taadmin', 'scripts/deployment/zero-downtime-deployment.sh',
                'scripts/deployment/version.properties')
        BBH.scans() == new ScanSettings(60, 120, 50, 30, 60, 60, 30, 30, true, 5)
        BBH.releaseGate() == new ReleaseGateSettings([SAST, SCA, NEXUS_IQ, DAST], true, 'release-gate.json')
        BBH.serviceDefaults() == new ServiceDefaults(GRADLE, VM, '.', 20)
        BBH.goldenFix() == GoldenFixPolicy.builder().enabled(true).onlyDirectDependencies(true).minThreatLevel(2)
                .ecosystems(['maven', 'npm', 'pypi'])
                .goldenVersionTypes(['recommended-non-breaking-with-dependencies', 'recommended-non-breaking'])
                .excludeDirs([]).verifyEnabled(true).verifyMaxAttempts(3).verifyTimeoutMinutes(20)
                .commitAuthorName('DevSecOps GoldenFix').commitAuthorEmail('devsecops-goldenfix@noreply.local').build()
        problems(BBH) == []
    }

    def "limits are kept in scanner order without missing entries and cannot be changed"() {
        given:
        def limits = new LinkedHashMap<Scanner, SeverityLimits>()
        limits.put(DAST, new SeverityLimits(1, 2, 3))
        limits.put(null, ZERO)
        limits.put(SCA, null)
        limits.put(SAST, new SeverityLimits(0, 5, 10))

        when:
        def values = copy(BBH, limits: limits)

        then:
        values.limits().keySet() as List == [SAST, DAST]
        values.limits() == [(SAST): new SeverityLimits(0, 5, 10), (DAST): new SeverityLimits(1, 2, 3)]
        copy(BBH, limits: null).limits() == [:]

        when:
        values.limits().put(SCA, ZERO)

        then:
        thrown(UnsupportedOperationException)
    }

    def "changing the platform keeps every other value"() {
        given:
        def platform = BBH.platform().withJenkinsUrl('https://jenkins.bbh.com')

        expect:
        BBH.withPlatform(platform) == copy(BBH, platform: platform)
    }

    def "validation reports the proxy pair, every scanner without limits and an incomplete GoldenFix policy"() {
        given:
        def values = copy(BBH, platform: copy(BBH.platform(), proxyPort: null),
                limits: [(SAST): ZERO, (SCA): ZERO], goldenFix: GoldenFixPolicy.inherit(true))
        def required = ['onlyDirectDependencies', 'minThreatLevel', 'verifyEnabled', 'verifyMaxAttempts',
                        'verifyTimeoutMinutes', 'commitAuthorName', 'commitAuthorEmail']

        expect:
        problems(values) == [new FieldProblem('platform.proxyPort', 'is required with a proxy host'),
                             new FieldProblem('limits.NEXUS_IQ', 'set the limits of every scanner'),
                             new FieldProblem('limits.DAST', 'set the limits of every scanner')] +
                required.collect { new FieldProblem("goldenFix.$it".toString(), 'is required in the global settings') } +
                [new FieldProblem('goldenFix.ecosystems', 'select at least one ecosystem'),
                 new FieldProblem('goldenFix.goldenVersionTypes', 'add at least one remediation type')]
    }

    def "the release gate checks a scanner, the line coverage minimum is at least 1 and GoldenFix lists fit"() {
        given:
        def values = copy(BBH, scans: copy(BBH.scans(), coverageMinLine: coverage),
                releaseGate: new ReleaseGateSettings(scanners, true, 'release-gate.json'),
                goldenFix: copy(BBH.goldenFix(), excludeDirs: excluded))

        expect:
        problems(values) == expected

        where:
        coverage | scanners | excluded                                                     || expected
        0        | []       | []                                                           || [COVERAGE, GATE]
        1        | [SAST]   | (1..15).collect { "folder-$it/${'x' * 150}".toString() }     || [EXCLUDED]
        null     | [DAST]   | []                                                           || []
    }

    def "the release gate state file can only be release-gate.json, the one file the library hands on: #stateFile"() {
        given:
        def values = copy(BBH, releaseGate: new ReleaseGateSettings([SAST], true, stateFile))

        expect:
        problems(values) == [new FieldProblem('releaseGate.stateFile', 'must be release-gate.json: the security'
                + ' pipeline archives and the extended pipeline copies only that file')]

        where:
        stateFile << ['dso-release-gate.json', 'Release-Gate.json', '  ', null]
    }

    static final FieldProblem COVERAGE = new FieldProblem('scans.coverageMinLine',
            'must be at least 1: the library replaces 0 with 60; turn off the coverage requirement of the release gate instead')
    static final FieldProblem GATE = new FieldProblem('releaseGate.scanners',
            'select at least one scanner: without any the library gates on all four')
    static final FieldProblem EXCLUDED = new FieldProblem('goldenFix.excludeDirs',
            'is too long: all entries together may take at most 2000 bytes')

    def "the library defaults are rendered in the shape and key order of its defaults.yaml"() {
        when:
        def defaults = BBH.defaultsConfig()
        def zero = [maxCritical: 0, maxHigh: 0, maxMedium: 0]

        then:
        defaults.keySet() as List == ['buildTool', 'deployTarget', 'sourceDir', 'coverage', 'tools', 'sast', 'sca',
                                      'dast', 'tests', 'releaseGate', 'goldenFix']
        defaults.subMap(['buildTool', 'deployTarget', 'sourceDir', 'coverage', 'tests']) ==
                [buildTool: 'gradle', deployTarget: 'vm', sourceDir: '.', coverage: [minLine: 60], tests: [maxParallel: 20]]
        defaults.tools == [sonar: [qualityGate: [waitForQualityGate: true, timeoutMinutes: 5]], nexusIq: zero]
        (defaults.sast as Map).keySet() as List == ['prepareTimeoutMin', 'pollTimeoutMin', 'pollIntervalSec'] + zero.keySet()
        defaults.sast == [prepareTimeoutMin: 120, pollTimeoutMin: 50, pollIntervalSec: 30] + zero
        defaults.sca == zero
        defaults.dast == [pollTimeoutMin: 60, pollIntervalSec: 60, reportTimeoutMin: 30, reportIntervalSec: 30] + zero
        defaults.releaseGate == [scanners: ['sast', 'sca', 'niq', 'dast'], requireCoverage: true,
                                 stateFile: 'release-gate.json']
        defaults.goldenFix == [enabled: true, onlyDirectDependencies: true, minThreatLevel: 2,
                               ecosystems: ['maven', 'npm', 'pypi'],
                               goldenVersionTypes: ['recommended-non-breaking-with-dependencies', 'recommended-non-breaking'],
                               verify: [enabled: true, maxAttempts: 3, timeoutMinutes: 20],
                               commitAuthorName: 'DevSecOps GoldenFix', commitAuthorEmail: 'devsecops-goldenfix@noreply.local']
    }

    def "the global GoldenFix policy runs GoldenFix unless it says otherwise and each scanner's limits get their section"() {
        given:
        def defaults = copy(BBH, limits: [(NEXUS_IQ): new SeverityLimits(1, 4, 9), (DAST): new SeverityLimits(0, 2, 20)])
                .defaultsConfig()

        expect:
        copy(BBH, goldenFix: INHERITED).goldenFix().enabled()
        !copy(BBH, goldenFix: GoldenFixPolicy.inherit(false)).goldenFix().enabled()
        defaults.tools.nexusIq == [maxCritical: 1, maxHigh: 4, maxMedium: 9]
        defaults.dast.subMap(['maxCritical', 'maxHigh', 'maxMedium']) == [maxCritical: 0, maxHigh: 2, maxMedium: 20]
        !defaults.sast.containsKey('maxCritical')
        !defaults.containsKey('sca')
    }

    private static List<FieldProblem> problems(GlobalSettingsValues values) {
        def problems = new ValidationProblems()
        values.validate(problems)
        problems.list()
    }
}
