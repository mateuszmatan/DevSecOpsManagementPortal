package com.bbh.itss.dso.portal.domain.dsoconfig

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.SshTarget
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.fullSettings
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class DsoConfigBuilderSpec extends Specification {

    @Subject
    def builder = new DsoConfigBuilder(storedSettings('https://jenkins.test').values())

    static final String REPOSITORY = 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner'
    static final Map GUI = [id: 10L, name: 'gui',
                            sonar: SonarSettings.of('CertScanner GUI', 'cert-gui', command(['sonarqube'])),
                            nexusIqApplications: [NexusIqApplication.of('cert-gui', ['**/build/libs/*.jar'])]]
    static final Map API = [id: 11L, name: 'backend-api', build: build(tool: MAVEN),
                            deployment: deployment(target: OPENSHIFT, appName: 'cert-api',
                                    artifactName: 'cert-api.jar')]

    Product certScanner = product(services: [GUI, API])
    Service gui = certScanner.services()[0]

    def "a product's config lists every service under projects in the template's key order"() {
        when:
        Map projects = builder.productConfig(certScanner).projects

        then:
        projects.keySet() as List == ['gui', 'backend-api']
        projects.gui.keySet() as List == ['appId', 'buildTool', 'deployTarget', 'sourceDir', 'javaPath', 'asoc', 'influx',
                                          'tools', 'dast', 'build', 'deploy']
        projects.gui.deploy.vm.keySet() as List == ['rd', 'qc']
        projects['backend-api'].appName == 'cert-api'
        projects['backend-api'].buildTool == 'maven'
        !projects['backend-api'].containsKey('deploy')
        projects['backend-api'].build == [maven: [goals: ['clean', 'verify']]]
    }

    def "every service names the BBH tool servers of the global settings next to its own values"() {
        when:
        Map entry = builder.productConfig(certScanner).projects.gui

        then:
        entry.asoc == [url: 'https://bbh.cloud.appscan.com', keyId: 'bbh_key-id', token: 'hcl-app-scan-account']
        entry.influx == [url: 'http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics&precision=s',
                         credentialsId: 'influxdb-token', enabled: true, project: 'CERT-gui', env: 'test']
        entry.tools.sonar == [serverUrl: 'https://tools.bbh.com/sonar', installationName: 'SonarQube',
                              projectName: 'CertScanner GUI', projectKey: 'cert-gui', gradle: [tasks: ['sonarqube']]]
        entry.tools.nexusIq == [serverUrl: 'https://tools.bbh.com/IQ', credentialsId: 'nexusiqP', application: 'cert-gui',
                                scanPatterns: ['**/build/libs/*.jar'], stage: 'build', failOnNetworkError: false]
    }

    def "a service's own servers, credentials and AppScan secret replace the global and product values"() {
        when:
        Map entry = builder.productConfig(product(services: [[id: 10L, name: 'gui', settings: fullSettings('gui')]]))
                .projects.gui

        then:
        entry.asoc.token == 'cert-appscan-secret'
        entry.influx.subMap(['url', 'credentialsId']) ==
                [url: 'https://influx.cert.bbh.com/api/v2/write', credentialsId: 'cert-influx']
        entry.tools.sonar.serverUrl == 'https://sonar.cert.bbh.com'
        entry.tools.nexusIq.subMap(['serverUrl', 'credentialsId']) ==
                [serverUrl: 'https://iq.cert.bbh.com', credentialsId: 'cert-iq']
        entry.tools.nexusIq.application.keySet() as List == ['cert-gui', 'cert-gui-batch']
        entry.tools.nexusIq.application.values()*.serverUrl.unique() == ['https://iq.cert.bbh.com']
        entry.tools.nexusIq.application.values()*.credentialsId.unique() == ['cert-iq']
    }

    def "the global deployment defaults fill in what a VM deployment leaves out"() {
        given:
        certScanner = product(services: [GUI, API, [id: 12L, name: 'batch',
                sshTargets: [(RD): SshTarget.builder().user('batchadm').deployDir('/opt/batch').build()],
                urbanCodeApplications: [UrbanCodeApplicationSettings.of('Batch', 1, ['RD'], null,
                        [UrbanCodeComponent.builder().componentName('batch-app').baseDir('build/libs')
                                .fileIncludePatterns('*.jar').incrementalVersion(false).build()])]]])

        when:
        Map deploy = builder.productConfig(certScanner).projects.batch.deploy

        then:
        deploy.vm.rd == [user: 'batchadm', deployDir: '/opt/batch', host: 'rdltaapps1.testbbh.com',
                         deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
                         versionFile: 'scripts/deployment/version.properties']
        deploy.vm.qc == [host: 'qcltaapps1.testbbh.com', user: 'taadmin',
                         deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
                         versionFile: 'scripts/deployment/version.properties']
        deploy.vm.dod.siteName == 'deploy.bbh.com'
        deploy.vm.dod.deployProcess == 'tomcat-app-process'
    }

    def "a pipeline receives its Jenkinsfile settings, the platform, the defaults and only its own service"() {
        when:
        def config = pipelineConfig(agentLabels: ['linux-agent', 'docker'])

        then:
        config.keySet() as List == ['pipeline', 'platform', 'defaults', 'projects']
        config.pipeline == [type: 'full', entryPoint: 'devSecOpsPipeline', product: 'CERT', projectNames: 'gui',
                            agentNames: ['linux-agent', 'docker']]
        config.platform.jenkinsUrl == 'https://jenkins.test'
        config.platform.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
        config.platform.environment.APPSCAN_HOST == 'bbh.cloud.appscan.com'
        config.platform.environment.PROXY_PORT == '9090'
        config.defaults.coverage == [minLine: 60]
        config.defaults.releaseGate == [scanners: ['sast', 'sca', 'niq', 'dast'], requireCoverage: true,
                                        stateFile: 'release-gate.json']
        config.projects.keySet() as List == ['gui']
        !config.projects.gui.containsKey('jenkins')
    }

    def "the #type pipeline names the job it works with"() {
        when:
        def config = pipelineConfig(type: type, extendedPipelineJob: extended, securityPipelineJob: security)

        then:
        config.pipeline.type == type.variant()
        config.pipeline.entryPoint == type.entryPoint()
        config.pipeline.securityPipeline == pipelineEntry
        config.projects.gui.jenkins == projectEntry

        where:
        type     | extended            | security            || pipelineEntry       | projectEntry
        SECURITY | 'CERT/gui-extended' | null                || null                | [pipeline: [extendedPipeline: 'CERT/gui-extended']]
        EXTENDED | null                | 'CERT/gui-security' || 'CERT/gui-security' | null
        SECURITY | null                | null                || null                | null
        NEXUS_IQ | 'CERT/gui-extended' | 'CERT/gui-security' || null                | null
    }

    def "the Nexus IQ GoldenFix pipeline renders the scan, GoldenFix and the repository of its golden pull request"() {
        given:
        certScanner = product(services: [GUI + [scm: ScmSettings.of(REPOSITORY, 'bitbucket-http-credentials'),
                                                goldenFix: GoldenFixPolicy.inherit(true)]])

        when:
        def config = builder.pipelineConfig(certScanner, certScanner.services()[0],
                pipeline(serviceId: 10L, type: NEXUS_IQ))

        then:
        config.pipeline == [type: 'nexusiq', entryPoint: 'devSecOpsNexusIqGoldenFixPipeline', product: 'CERT',
                            projectNames: 'gui', agentNames: ['linux-agent']]
        config.projects.gui.tools.nexusIq.application == 'cert-gui'
        config.projects.gui.tools.nexusIq.scanPatterns == ['**/build/libs/*.jar']
        config.projects.gui.scm.bitbucket.subMap(['url', 'credentialsId']) ==
                [url: REPOSITORY, credentialsId: 'bitbucket-http-credentials']
        config.projects.gui.goldenFix == [enabled: true]
        config.defaults.goldenFix.verify.enabled != null
    }

    def "the global configuration holds the platform and the library defaults"() {
        when:
        def config = builder.globalConfig()

        then:
        config.keySet() as List == ['platform', 'defaults']
        config.defaults.keySet() as List == ['buildTool', 'deployTarget', 'sourceDir', 'coverage', 'tools', 'sast', 'sca',
                                             'dast', 'tests', 'releaseGate', 'goldenFix']
        config.defaults.tools.nexusIq == [maxCritical: 0, maxHigh: 0, maxMedium: 0]

        when:
        new DsoConfigBuilder(null)

        then:
        thrown(NullPointerException)
    }

    private Map<String, Object> pipelineConfig(Map args = [:]) {
        builder.pipelineConfig(certScanner, gui, pipeline(args + [serviceId: gui.id()]))
    }
}
