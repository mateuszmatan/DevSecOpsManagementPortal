package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy

class PlatformSettingsSpec extends Specification {

    static final String LINUX_CLIENT = 'https://tools.bbh.com/nexus/repository/releases/com/bbh/appscan/SAClientUtil/' +
            '8.0.1646_Linux/SAClientUtil-8.0.1646_Linux-SAClientUtil_8.0.1646_Linux.zip'
    static final String WINDOWS_CLIENT = 'https://tools.bbh.com/nexus/repository/releases/com/bbh/appscan/SAClientUtil/' +
            '8.0.1646_Win/SAClientUtil-8.0.1646_Win-SAClientUtil_8.0.1646_Win.zip'
    static final String INFLUX_WRITE = 'http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics&precision=s'

    def bbh = bbhDefaults().platform()

    def "the platform section holds the BBH tools and the environment the library sets for AppScan"() {
        when:
        def config = bbh.toConfig()

        then:
        config.keySet() as List == ['jenkinsLibrary', 'environment', 'oisHost', 'nexusSnapshotRepositoryUrl',
                                    'nexusSnapshotRepositoryId', 'iosBuildAgent']
        config.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
        config.oisHost == 'oisapi.bbh.com'
        config.nexusSnapshotRepositoryUrl == 'http://tools.bbh.com/nexus/content/repositories/snapshots/'
        config.nexusSnapshotRepositoryId == 'bbh-snapshots'
        config.iosBuildAgent == 'mac002.bbh.com'
        (config.environment as Map).keySet() as List == ['APPSCAN_SERVER_URL', 'APPSCAN_HOST', 'SA_LINUX_URL',
                                                         'SA_WIN_URL', 'PROXY_HOST', 'PROXY_PORT', 'PROXY_USER']
        config.environment == [APPSCAN_SERVER_URL: 'https://bbh.cloud.appscan.com',
                               APPSCAN_HOST      : 'bbh.cloud.appscan.com',
                               SA_LINUX_URL      : LINUX_CLIENT,
                               SA_WIN_URL        : WINDOWS_CLIENT,
                               PROXY_HOST        : 'tstproxy.bbh.com',
                               PROXY_PORT        : '9090',
                               PROXY_USER        : 'PROXY_ASOCJenk']
    }

    def "the AppScan host is #host for the AppScan URL #url"() {
        expect:
        copy(bbh, asocUrl: url).toConfig().environment.APPSCAN_HOST == host

        where:
        url                                    || host
        'https://eu.cloud.appscan.com/api/v4'  || 'eu.cloud.appscan.com'
        'http://appscan.bbh.com:8443'          || 'appscan.bbh.com'
        'https://bbh cloud appscan.com'        || null
        'https:///no-host'                     || null
    }

    def "a platform without AppScan or proxy has no environment, and without a port only host and user are passed"() {
        given:
        def bare = copy(bbh, asocUrl: null, appScanClientLinuxUrl: null, appScanClientWindowsUrl: null, proxyHost: null,
                proxyPort: null, proxyUser: null, oisHost: null, nexusSnapshotRepositoryUrl: null,
                nexusSnapshotRepositoryId: null, iosBuildAgent: ' ')

        expect:
        bare.toConfig() == [jenkinsLibrary: 'DevSecOpsJenkinsLibrary']
        copy(bbh, proxyPort: null).toConfig().environment.subMap(['PROXY_HOST', 'PROXY_PORT', 'PROXY_USER']) ==
                [PROXY_HOST: 'tstproxy.bbh.com', PROXY_USER: 'PROXY_ASOCJenk']
    }

    def "blank values are stored as null and the others trimmed"() {
        when:
        def platform = new PlatformSettings(' https://jenkins.bbh.com ', ' DevSecOpsJenkinsLibrary ', ' ', '', null,
                ' tstproxy.bbh.com ', 9090, '  ', ' oisapi.bbh.com ', ' https://tools.bbh.com/sonar ', ' SonarQube ',
                ' https://tools.bbh.com/IQ ', ' nexusiqP ', ' ', ' ', ' ', ' ', ' mac002.bbh.com ')

        then:
        platform == new PlatformSettings('https://jenkins.bbh.com', 'DevSecOpsJenkinsLibrary', null, null, null,
                'tstproxy.bbh.com', 9090, null, 'oisapi.bbh.com', 'https://tools.bbh.com/sonar', 'SonarQube',
                'https://tools.bbh.com/IQ', 'nexusiqP', null, null, null, null, 'mac002.bbh.com')
    }

    def "changing the Jenkins URL keeps every other value and puts the URL first"() {
        when:
        def changed = bbh.withJenkinsUrl('  https://jenkins.bbh.com/  ')

        then:
        changed == copy(bbh, jenkinsUrl: 'https://jenkins.bbh.com/')
        (changed.toConfig().keySet() as List).first() == 'jenkinsUrl'
        bbh.jenkinsUrl() == null
        changed.withJenkinsUrl(' ').jenkinsUrl() == null
    }

    def "every project entry names the tool servers and credentials that are set"() {
        given:
        def tree = new ConfigTree()
        def bare = new ConfigTree()

        when:
        bbh.writeProjectDefaults(tree)
        copy(bbh, influxWriteUrl: null, influxCredentialsId: null).writeProjectDefaults(bare)

        then:
        tree.toMap() == [asoc  : [url: 'https://bbh.cloud.appscan.com'],
                         influx: [url: INFLUX_WRITE, credentialsId: 'influxdb-token'],
                         tools : [sonar  : [serverUrl: 'https://tools.bbh.com/sonar', installationName: 'SonarQube'],
                                  nexusIq: [serverUrl: 'https://tools.bbh.com/IQ', credentialsId: 'nexusiqP']]]
        bare.toMap() == tree.toMap().findAll { it.key != 'influx' }
    }

    def "a proxy host #proxyHost with port #proxyPort reports #problems"() {
        given:
        def found = new ValidationProblems()

        when:
        copy(bbh, proxyHost: proxyHost, proxyPort: proxyPort).validate(found.at('platform'))

        then:
        found.list() == problems

        where:
        proxyHost          | proxyPort || problems
        'tstproxy.bbh.com' | 9090      || []
        null               | null      || []
        'tstproxy.bbh.com' | null      || [new FieldProblem('platform.proxyPort', 'is required with a proxy host')]
        null               | 8080      || [new FieldProblem('platform.proxyHost', 'is required with a proxy port')]
    }
}
