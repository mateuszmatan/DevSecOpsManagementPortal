package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings.DEFAULTS
import static com.bbh.itss.dso.portal.domain.shared.Sections.written
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy

class DeploymentTargetsSpec extends Specification {

    static final UrbanCodeComponent GUI_COMPONENT = UrbanCodeComponent.builder().componentName('cert-gui')
            .baseDir('build/libs').fileIncludePatterns('*.war').fileExcludePatterns('*-plain.war').versionPrefix('gui-')
            .version('1.0.0').incrementalVersion(false).build()

    def "an SSH target trims its values, writes the ones that are set in the library's order and is empty without any"() {
        when:
        def target = new SshTarget(' rdltaapps1.testbbh.com ', ' dsoadm ', ' /opt/cert ', ' deploy.sh ', ' version.txt ')
        def blank = new SshTarget(' ', '', null, ' ', null)

        then:
        target.toConfig() == [host        : 'rdltaapps1.testbbh.com', user: 'dsoadm', deployDir: '/opt/cert',
                              deployScript: 'deploy.sh', versionFile: 'version.txt']
        target.toConfig().keySet() as List == ['host', 'user', 'deployDir', 'deployScript', 'versionFile']
        !target.isEmpty()
        SshTarget.builder().deployDir('/opt/cert').build().toConfig() == [deployDir: '/opt/cert']
        blank == new SshTarget(null, null, null, null, null)
        blank.isEmpty()
        blank.toConfig() == [:]
        Region.values()*.configKey() == ['rd', 'qc']
    }

    def "OpenShift field #field is written as #config"() {
        expect:
        openShift((field): value).toConfig() == config
        !openShift((field): value).isEmpty()

        where:
        field                         | value                        || config
        'projectBuild'                | 'cert-build'                 || [projectBuildR: 'cert-build']
        'buildConfigPath'             | 'openshift/bc.yaml'          || [buildConfigPath: 'openshift/bc.yaml']
        'dockerFilePath'              | 'Dockerfile'                 || [dockerFilePath: 'Dockerfile']
        'buildContext'                | 'gui'                        || [buildContext: 'gui']
        'addFile'                     | 'target/cert.jar'            || [addFile: 'target/cert.jar']
        'dockerRepoPush'              | 'nexus-push.bbh.com/cert'    || [qcDockerRepoPush: 'nexus-push.bbh.com/cert']
        'dockerRepoPull'              | 'nexus-pull.bbh.com/cert'    || [qcDockerRepoPull: 'nexus-pull.bbh.com/cert']
        'certDir'                     | '/etc/pki/openshift'         || [openshiftCertDir: '/etc/pki/openshift']
        'nexusAuthFile'               | '/home/jenkins/auth.json'    || [nexus: [authfile: '/home/jenkins/auth.json']]
        'projectDeployment'           | 'cert-rd'                    || [projectDeploymentR: 'cert-rd']
        'deployConfigPath'            | 'openshift/dc.yaml'          || [deployConfigPath: 'openshift/dc.yaml']
        'configPath'                  | 'openshift/config'           || [configPathR: 'openshift/config']
        'skipConfigDeploy'            | true                         || [skipConfigDeploy: true]
        'healthCheckUrl'              | '/actuator/health'           || [healthCheckUrl: '/actuator/health']
        'routeHostname'               | 'cert-rd.apps.bbh.com'       || [routeHostnameR: 'cert-rd.apps.bbh.com']
        'deploymentPath'              | 'k8s/rd'                     || [deploymentPath: 'k8s/rd']
        'deploymentRepoUrl'           | 'git@bitbucket:ta/deploy.git' || [deploymentRepo: [url: 'git@bitbucket:ta/deploy.git']]
        'deploymentRepoBranch'        | 'main'                       || [deploymentRepo: [branch: 'main']]
        'deploymentRepoCredentialsId' | 'bb-creds'                   || [deploymentRepo: [credentials: 'bb-creds']]
        'buildTag'                    | '1.0.42'                     || [buildTag: '1.0.42']
        'internalDockerUrl'           | 'registry.svc:5000/cert'     || [internalDockerUrl: 'registry.svc:5000/cert']
    }

    def "a full OpenShift target writes every key in the library's order"() {
        when:
        def config = new OpenShiftTarget(' cert-build ', 'bc.yaml', 'Dockerfile', '.', 'cert.jar', 'push/cert', 'pull/cert',
                '/certs', 'auth.json', 'cert-rd', 'dc.yaml', 'config', true, '/health', 'cert.apps', 'k8s', 'ssh://git@b/d.git',
                'main', 'bb-creds', null, null).toConfig()

        then:
        config == [projectBuildR     : 'cert-build', buildConfigPath: 'bc.yaml', dockerFilePath: 'Dockerfile', buildContext: '.',
                   addFile           : 'cert.jar', qcDockerRepoPush: 'push/cert', qcDockerRepoPull: 'pull/cert',
                   openshiftCertDir  : '/certs', nexus: [authfile: 'auth.json'], projectDeploymentR: 'cert-rd',
                   deployConfigPath  : 'dc.yaml', configPathR: 'config', healthCheckUrl: '/health', routeHostnameR: 'cert.apps',
                   deploymentPath    : 'k8s', deploymentRepo: [url: 'ssh://git@b/d.git', branch: 'main', credentials: 'bb-creds'],
                   skipConfigDeploy  : true]
        config.keySet() as List == ['projectBuildR', 'buildConfigPath', 'dockerFilePath', 'buildContext', 'addFile',
                                    'qcDockerRepoPush', 'qcDockerRepoPull', 'openshiftCertDir', 'nexus', 'projectDeploymentR',
                                    'deployConfigPath', 'configPathR', 'healthCheckUrl', 'routeHostnameR', 'deploymentPath',
                                    'deploymentRepo', 'skipConfigDeploy']
    }

    def "an OpenShift target of blank values that deploys its configuration is empty"() {
        when:
        def target = new OpenShiftTarget(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', null, ' ', ' ', ' ', ' ',
                ' ', ' ', null, null)

        then:
        target == openShift([:])
        !target.skipConfigDeploy()
        target.projectBuild() == null
        target.deploymentRepoCredentialsId() == null
        target.isEmpty()
        target.toConfig() == [:]
        openShift(skipConfigDeploy: false).isEmpty()
    }

    def "UrbanCode settings default to deploying a snapshot of only the deployed versions and write nothing without applications"() {
        expect:
        new UrbanCodeSettings(' ', ' ', null, null, null, null, null, ' ', ' ') == DEFAULTS
        DEFAULTS == new UrbanCodeSettings(null, null, false, true, false, true, false, null, null)
        written { DEFAULTS.writeTo(it, []) } == [:]
        written { fullUrbanCode().writeTo(it, []) } == [:]
    }

    def "UrbanCode settings with applications write deploy.vm.dod with the library's defaults"() {
        given:
        def app = UrbanCodeApplicationSettings.of('Cert', null, [], null, [])

        expect:
        written { DEFAULTS.writeTo(it, [app]) } ==
                [deploy: [vm: [dod: [skipWait                 : false, deployWithSnapshot: true, updateSnapshotComp: false,
                                     includeOnlyDeployVersions: true, deployOnlyChanged: false,
                                     applications             : [[applicationName: 'Cert']]]]]]
    }

    def "every UrbanCode option is written with the applications in their order"() {
        given:
        def first = UrbanCodeApplicationSettings.of('Cert', 1, ['RD'], null, [GUI_COMPONENT])
        def second = UrbanCodeApplicationSettings.of('Cert Batch', 2, [], 'batch-snap', [])

        when:
        def dod = written { fullUrbanCode().writeTo(it, [first, second]) }.deploy.vm.dod

        then:
        dod == [siteName                 : 'BBH-RD', deployProcess: 'Deploy Cert', skipWait: true, deployWithSnapshot: false,
                updateSnapshotComp       : true, includeOnlyDeployVersions: false, deployOnlyChanged: true,
                deployDescription        : 'Deployed by Jenkins', requestProperties: 'key=value',
                applications             : [first.toConfig(), second.toConfig()]]
        dod.applications*.applicationName == ['Cert', 'Cert Batch']
    }

    def "an UrbanCode application trims its values, keeps each environment once and copies its components"() {
        given:
        def components = [GUI_COMPONENT]

        when:
        def app = UrbanCodeApplicationSettings.of(' Cert ', 3, [' RD ', 'RD', '', 'QC'], ' ', components)
        components << UrbanCodeComponent.builder().componentName('other').build()

        then:
        app == UrbanCodeApplicationSettings.of('Cert', 3, ['RD', 'QC'], null, [GUI_COMPONENT])
        UrbanCodeApplicationSettings.of(null, null, null, null, null) == UrbanCodeApplicationSettings.of(null, null, [], null, [])
    }

    def "an UrbanCode application is written with its components"() {
        when:
        def entry = UrbanCodeApplicationSettings.of('Cert', 2, ['RD', 'RD2'], 'cert-snap', [GUI_COMPONENT]).toConfig()

        then:
        entry == [applicationName: 'Cert', order: 2, environments: ['RD', 'RD2'], snapshotName: 'cert-snap',
                  components     : [[componentName      : 'cert-gui', baseDir: 'build/libs', fileIncludePatterns: '*.war',
                                     fileExcludePatterns: '*-plain.war', versionPrefix: 'gui-', version: '1.0.0',
                                     incrementalVersion : false]]]
        entry.keySet() as List == ['applicationName', 'order', 'environments', 'snapshotName', 'components']
        UrbanCodeApplicationSettings.of('Cert', null, [], null, []).toConfig() == [applicationName: 'Cert']
    }

    def "an UrbanCode application overrides the service's options with its own, false included"() {
        when:
        def entry = UrbanCodeApplicationSettings.builder().applicationName('Cert').siteName(' BBH-QC ')
                .deployProcess(' Deploy QC ').skipWait(false).updateSnapshotComponents(true)
                .includeOnlyDeployVersions(false).deployDescription(' release ').description(' Cert ')
                .requestProperties(' a=b ').components([GUI_COMPONENT]).build().toConfig()

        then:
        entry.keySet() as List == ['applicationName', 'siteName', 'deployProcess', 'skipWait', 'updateSnapshotComp',
                                   'includeOnlyDeployVersions', 'deployDescription', 'description', 'requestProperties',
                                   'components']
        entry.subMap(['siteName', 'deployProcess', 'skipWait', 'updateSnapshotComp', 'includeOnlyDeployVersions',
                      'deployDescription', 'description', 'requestProperties']) ==
                [siteName                 : 'BBH-QC', deployProcess: 'Deploy QC', skipWait: false, updateSnapshotComp: true,
                 includeOnlyDeployVersions: false, deployDescription: 'release', description: 'Cert',
                 requestProperties        : 'a=b']
    }

    def "an UrbanCode component writes the push options that are set"() {
        expect:
        UrbanCodeComponent.builder().componentName('cert-gui').baseDir('build/libs').fileIncludePatterns('*.war')
                .extensions(' war,jar ').charset(' UTF-8 ').pushDescription(' push ').versionProperties(' build=1 ')
                .versionDescription(' version ').build().toConfig() ==
                [componentName     : 'cert-gui', baseDir: 'build/libs', fileIncludePatterns: '*.war',
                 incrementalVersion: true, extensions: 'war,jar', charset: 'UTF-8', pushDescription: 'push',
                 versionProperties : 'build=1', versionDescription: 'version']
        UrbanCodeComponent.of('cert-gui', 'build/libs', '*.war') ==
                new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', null, null, null, true, null, null, null, null,
                        null)
    }

    def "an UrbanCode component trims its values and publishes incremental versions by default"() {
        when:
        def component = new UrbanCodeComponent(' cert-gui ', ' ', ' ', ' ', ' ', ' ', null, null, null, null, null, null)

        then:
        component == new UrbanCodeComponent('cert-gui', null, null, null, null, null, true, null, null, null, null, null)
        component.toConfig() == [componentName: 'cert-gui', incrementalVersion: true]
        component.toConfig().keySet() as List == ['componentName', 'incrementalVersion']
        !UrbanCodeComponent.builder().incrementalVersion(false).build().incrementalVersion()
        GUI_COMPONENT.toConfig().keySet() as List == ['componentName', 'baseDir', 'fileIncludePatterns',
                                                       'fileExcludePatterns', 'versionPrefix', 'version', 'incrementalVersion']
    }

    private static UrbanCodeSettings fullUrbanCode() {
        new UrbanCodeSettings(' BBH-RD ', ' Deploy Cert ', true, false, true, false, true, ' Deployed by Jenkins ', ' key=value ')
    }

    private static OpenShiftTarget openShift(Map fields) {
        copy(fields, OpenShiftTarget.builder().build())
    }
}
