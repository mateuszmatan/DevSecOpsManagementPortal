package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APPBUNDLE

class ConfigTreeSpec extends Specification {

    def tree = new ConfigTree()

    def "values, false, zero, flags that are on and enumerated values in lower case are written by dotted path"() {
        when:
        tree.set('tools.sonar.projectKey', 'cert-scanner')
                .set('appId', '109f44ac')
                .set('dast.enabled', false)
                .set('tests.maxParallel', 2).set('tests.maxParallel', 0).set('tests.maxParallel', null)
                .set('tests.smoke.jobs', [[job: 'CERT/smoke']])
                .set('build.gradle.env', [CI: 'true'])
                .set('flutter.platform', APPBUNDLE)
                .flag('flutter.sonar', true)
                .flag('flutter.skipped', false)
                .set('influx', 'on').set('influx.enabled', true)

        then:
        tree.toMap() == [tools  : [sonar: [projectKey: 'cert-scanner']], appId: '109f44ac', dast: [enabled: false],
                         tests  : [maxParallel: 0, smoke: [jobs: [[job: 'CERT/smoke']]]], build: [gradle: [env: [CI: 'true']]],
                         flutter: [platform: 'appbundle', sonar: true], influx: [enabled: true]]
    }

    def "a missing value is skipped without creating a section on its way: #value"() {
        when:
        tree.set('tools.sonar.projectKey', value)

        then:
        tree.toMap() == [:]
        tree.get('tools') == null

        where:
        value << [null, '', '  ', [], [:], [] as Set]
    }

    def "a default is filled only into a section that is there and never replaces the service's own value"() {
        given:
        tree.set('deploy.vm.rd.deployDir', '/opt/cert').set('deploy.vm.qc.host', 'own.host').set('appId', 'x')
                .set('tests.smoke.enabled', true)

        when:
        tree.fillIn('deploy.vm.rd', 'host', 'rdltaapps1.testbbh.com').fillIn('deploy.vm.rd', 'user', 'dsoadm')
                .fillIn('deploy.vm.rd', 'versionFile', ' ').fillIn('deploy.vm.rd', 'applications', [])
                .fillIn('deploy.vm.qc', 'host', 'default.host')
                .fillIn('deploy.vm.dod', 'siteName', 'BBH')
                .fillIn('appId', 'value', 'y')
                .fillIn('deploy.vm.qc.host', 'user', 'dsoadm')
                .fillIn('tests', 'maxParallel', 2).fillIn('tests.smoke', 'timeoutMin', 15)

        then:
        tree.toMap() == [deploy: [vm: [rd: [deployDir: '/opt/cert', host: 'rdltaapps1.testbbh.com', user: 'dsoadm'],
                                       qc: [host: 'own.host']]],
                         appId : 'x', tests: [smoke: [enabled: true, timeoutMin: 15], maxParallel: 2]]
        tree.get('deploy.vm.rd').keySet() as List == ['deployDir', 'host', 'user']
    }

    def "a value set only when absent creates its section and never replaces a value that is there"() {
        given:
        tree.set('deploy.vm.rd.host', 'own.host')

        when:
        tree.setIfAbsent('deploy.vm.rd.host', 'default.host')
                .setIfAbsent('deploy.vm.rd.user', 'taadmin')
                .setIfAbsent('deploy.vm.qc.host', 'qc.host')
                .setIfAbsent('deploy.vm.qc.user', null)

        then:
        tree.toMap() == [deploy: [vm: [rd: [host: 'own.host', user: 'taadmin'], qc: [host: 'qc.host']]]]
    }

    def "get follows the path and the map lists the given keys first and is a copy"() {
        given:
        tree.set('tests.enabled', true).set('appId', 'x').set('flutter.platform', 'apk').set('buildTool', 'gradle')

        when:
        tree.toMap().put('appId', 'changed')

        then:
        tree.get('appId') == 'x'
        tree.get('flutter') == [platform: 'apk']
        tree.get('flutter.platform.name') == null
        tree.get('tools.nexusIq.application') == null
        tree.toMap(['appId', 'buildTool', 'deployTarget', 'tests']).keySet() as List == ['appId', 'buildTool', 'tests', 'flutter']
        tree.toMap().keySet() as List == ['tests', 'appId', 'flutter', 'buildTool']
    }
}
