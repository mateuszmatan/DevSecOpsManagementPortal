package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APK
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APPBUNDLE
import static com.bbh.itss.dso.portal.domain.catalog.FlutterSettings.NONE
import static com.bbh.itss.dso.portal.domain.shared.Sections.problems
import static com.bbh.itss.dso.portal.domain.shared.Sections.reported
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class FlutterSettingsSpec extends Specification {

    static final FlutterSettings FULL = new FlutterSettings(APPBUNDLE, ['app', 'core'], ['app'], ['core'], ['plugin_a'],
            'flutter-signing', 'flutter-prod-license', 'flutter-test-license', 'com.bbh.cert', 'cert-app',
            'com.bbh:flutter-delivery:1.0', 'lib', 'test', true, 'dart analyze --fatal-infos', '5.0.1.3006')

    def "Flutter settings trim their values, keep each module once and write nothing when they set nothing"() {
        expect:
        new FlutterSettings(null, [' app ', 'app', ''], null, [' core '], [' '], *([' '] * 8), null, ' ', ' ') ==
                new FlutterSettings(null, ['app'], [], ['core'], [], *([null] * 8), false, null, null)
        NONE == new FlutterSettings(*([null] * 16))
        written(NONE) == [:]
    }

    def "every Flutter setting is written under the key the library reads"() {
        expect:
        written(FULL) == [flutter : [platform: 'appbundle'],
                          tools   : [flutter: [flutterModules: ['app', 'core']],
                                     sonar  : [sources            : 'lib', tests: 'test', dartAnalyzeCommand: 'dart analyze --fatal-infos',
                                               sonarScannerVersion: '5.0.1.3006', flutterPlugin: true]],
                          tests   : [modules: ['app'], submodules: ['core'], subplugins: ['plugin_a']],
                          build   : [credentialsId: ['flutter-signing', 'flutter-prod-license', 'flutter-test-license']],
                          delivery: [group: 'com.bbh.cert', artifact: 'cert-app', plugin: 'com.bbh:flutter-delivery:1.0']]
    }

    def "the build credentials are written only when all three are set: #signing, #prod, #test"() {
        expect:
        written(credentials(signing, prod, test)).build?.credentialsId == expected

        where:
        signing | prod   | test   || expected
        'sign'  | 'prod' | 'test' || ['sign', 'prod', 'test']
        null    | 'prod' | 'test' || null
        'sign'  | null   | 'test' || null
        'sign'  | 'prod' | null   || null
        null    | null   | null   || null
    }

    def "the SonarQube Flutter plugin is written only when it is used"() {
        expect:
        written(FlutterSettings.builder().platform(APK).sonarSources('lib').build()) ==
                [flutter: [platform: 'apk'], tools: [sonar: [sources: 'lib']]]
    }

    def "the Flutter build stage needs its modules, a test module and all three credentials"() {
        when:
        def problems = reported { NONE.validate(it, OPENSHIFT) }

        then:
        problems*.field == ['modules', 'testModules', 'signingPasswordCredentialsId', 'prodLicenseCredentialsId',
                            'testLicenseCredentialsId']
        problems*.message == ['add at least one module: the build stage prepares each of them',
                              'add at least one test module: the unit tests stage runs them'] +
                ['is required: the Flutter build stage reads this Jenkins credential'] * 3
    }

    def "a Flutter build delivered to VMs needs the Nexus coordinates of its delivery"() {
        when:
        def problems = reported {
            FlutterSettings.builder().platform(APK).modules(['app']).testModules(['app'])
                    .signingPasswordCredentialsId('sign').prodLicenseCredentialsId('prod')
                    .testLicenseCredentialsId('test').deliveryGroup(group).deliveryArtifact(artifact)
                    .deliveryPlugin(plugin).build().validate(it, target)
        }

        then:
        problems*.field == missing
        problems*.message.every { it == 'is required for Flutter on VMs: the Nexus delivery uploads the build under it' }

        where:
        target    | group     | artifact | plugin   || missing
        VM        | null      | null     | null     || ['deliveryGroup', 'deliveryArtifact', 'deliveryPlugin']
        VM        | 'com.bbh' | ' '      | 'plugin' || ['deliveryArtifact']
        VM        | 'com.bbh' | 'app'    | 'plugin' || []
        OPENSHIFT | null      | null     | null     || []
    }

    def "module lists that do not fit their column are refused"() {
        given:
        def tooMany = (1..30).collect { "modules/feature-$it/${'x' * 25}".toString() }
        def settings = FlutterSettings.builder().platform(APK).modules(tooMany).testModules(tooMany)
                .testSubmodules(tooMany).testSubplugins(['ok']).signingPasswordCredentialsId('sign')
                .prodLicenseCredentialsId('prod').testLicenseCredentialsId('test').build()

        when:
        def problems = reported { settings.validate(it, OPENSHIFT) }

        then:
        problems*.field == ['modules', 'testModules', 'testSubmodules']
        problems*.message.unique() == ['is too long: all entries together may take at most 1000 bytes']
    }

    def "missing credential #missing is reported"() {
        expect:
        problems { settings.validate(it, VM) } == missing

        where:
        settings                             || missing
        FULL                                 || []
        credentials(null, 'prod', 'test')    || ['signingPasswordCredentialsId']
        credentials('sign', ' ', 'test')     || ['prodLicenseCredentialsId']
        credentials('sign', 'prod', null)    || ['testLicenseCredentialsId']
    }

    private static FlutterSettings credentials(String signing, String prod, String test) {
        FlutterSettings.builder().modules(['app']).testModules(['app']).signingPasswordCredentialsId(signing)
                .prodLicenseCredentialsId(prod).testLicenseCredentialsId(test).deliveryGroup('com.bbh')
                .deliveryArtifact('app').deliveryPlugin('plugin').build()
    }
}
