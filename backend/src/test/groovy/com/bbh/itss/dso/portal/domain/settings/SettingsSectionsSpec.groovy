package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA
import static com.bbh.itss.dso.portal.domain.settings.SeverityLimits.ZERO
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class SettingsSectionsSpec extends Specification {

    static final String SCRIPT = 'scripts/deployment/zero-downtime-deployment.sh'
    static final String VERSION = 'scripts/deployment/version.properties'

    def deployment = bbhDefaults().deployment()

    def "the #scanner scanner is #gateKey in the release gate and keeps its limits under #path"() {
        expect:
        scanner.gateKey() == gateKey
        scanner.defaultsPath() == path

        where:
        scanner  || gateKey | path
        SAST     || 'sast'  | 'sast'
        SCA      || 'sca'   | 'sca'
        NEXUS_IQ || 'niq'   | 'tools.nexusIq'
        DAST     || 'dast'  | 'dast'
    }

    def "limits, service defaults and scan settings are written as the library's values"() {
        expect:
        written { new SeverityLimits(1, 2, 3).writeTo(it, 'tools.nexusIq') } ==
                [tools: [nexusIq: [maxCritical: 1, maxHigh: 2, maxMedium: 3]]]
        ZERO == new SeverityLimits(0, 0, 0)
        written { new ServiceDefaults(MAVEN, OPENSHIFT, 'src', 4).writeTo(it) } ==
                [buildTool: 'maven', deployTarget: 'openshift', sourceDir: 'src', tests: [maxParallel: 4]]
        written { new ScanSettings(80, 10, 20, 15, 45, 55, 65, 75, false, 9).writeTo(it) } ==
                [coverage: [minLine: 80],
                 tools   : [sonar: [qualityGate: [waitForQualityGate: false, timeoutMinutes: 9]]],
                 sast    : [prepareTimeoutMin: 10, pollTimeoutMin: 20, pollIntervalSec: 15],
                 dast    : [pollTimeoutMin: 45, pollIntervalSec: 55, reportTimeoutMin: 65, reportIntervalSec: 75]]
    }

    def "service defaults use the source root #expected for #sourceDir"() {
        expect:
        new ServiceDefaults(MAVEN, OPENSHIFT, sourceDir, 4).sourceDir() == expected

        where:
        sourceDir || expected
        null      || '.'
        '   '     || '.'
        ' app '   || 'app'
    }

    def "the release gate keeps each scanner once in scanner order, trims its state file and writes the gate keys"() {
        when:
        def gate = new ReleaseGateSettings([DAST, SAST, DAST, NEXUS_IQ], false, '  gate.json  ')

        then:
        gate.scanners() == [SAST, NEXUS_IQ, DAST]
        gate.stateFile() == 'gate.json'
        new ReleaseGateSettings(null, true, '  ').with { [scanners(), stateFile()] } == [[], null]
        written { gate.writeTo(it) } ==
                [releaseGate: [scanners: ['sast', 'niq', 'dast'], requireCoverage: false, stateFile: 'gate.json']]
        written { new ReleaseGateSettings([], true, 'release-gate.json').writeTo(it) } ==
                [releaseGate: [requireCoverage: true, stateFile: 'release-gate.json']]
    }

    def "deployment defaults add nothing to OpenShift services or when no value is known"() {
        expect:
        written { it.set('deployTarget', 'openshift'); deployment.fillIn(it, OPENSHIFT) } ==
                [deployTarget: 'openshift']
        written { DeploymentDefaults.builder().build().fillIn(it, VM) } == [:]
        new DeploymentDefaults(' site ', ' process ', ' rd.bbh.com ', ' qc.bbh.com ', ' user ', ' ', null) ==
                new DeploymentDefaults('site', 'process', 'rd.bbh.com', 'qc.bbh.com', 'user', null, null)
    }

    def "a VM service without SSH values of its own deploys to the global RD and QC hosts"() {
        expect:
        written { deployment.fillIn(it, VM) } ==
                [deploy: [vm: [rd: [host: 'rdltaapps1.testbbh.com', user: 'taadmin', deployScript: SCRIPT, versionFile: VERSION],
                               qc: [host: 'qcltaapps1.testbbh.com', user: 'taadmin', deployScript: SCRIPT, versionFile: VERSION]]]]
    }

    def "a changed global host reaches every VM service that does not set its own"() {
        given:
        def moved = new DeploymentDefaults('BBH', 'process', 'rdnew.testbbh.com', 'qcnew.testbbh.com', 'dsoadm', null, null)

        expect:
        written { it.set('deploy.vm.qc.host', 'own-qc.testbbh.com'); moved.fillIn(it, VM) } ==
                [deploy: [vm: [qc: [host: 'own-qc.testbbh.com', user: 'dsoadm'], rd: [host: 'rdnew.testbbh.com', user: 'dsoadm']]]]
    }

    def "deployment defaults complete the sections a service configured and keep its own values"() {
        given:
        def tree = new ConfigTree()
                .set('deploy.vm.dod.siteName', 'own-site')
                .set('deploy.vm.rd.user', 'batchadm')
                .set('deploy.vm.rd.deployDir', '/opt/batch')
                .set('deploy.vm.qc.deployDir', '/opt/app')

        when:
        deployment.fillIn(tree, VM)

        then:
        tree.get('deploy.vm.dod') == [siteName: 'own-site', deployProcess: 'tomcat-app-process']
        tree.get('deploy.vm.rd').keySet() as List == ['user', 'deployDir', 'host', 'deployScript', 'versionFile']
        tree.get('deploy.vm.rd') == [user: 'batchadm', deployDir: '/opt/batch', host: 'rdltaapps1.testbbh.com',
                                     deployScript: SCRIPT, versionFile: VERSION]
        tree.get('deploy.vm.qc') == [deployDir: '/opt/app', host: 'qcltaapps1.testbbh.com', user: 'taadmin',
                                     deployScript: SCRIPT, versionFile: VERSION]
    }
}
