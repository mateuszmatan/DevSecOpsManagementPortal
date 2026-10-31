package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.catalog.DatabaseSettings
import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.databaseDefaults

class DatabaseDefaultsSpec extends Specification {

    static final String SHA = 'a' * 64

    def "blank values are not set, Java 17 is the library's own, and the allowed databases are listed once"() {
        given:
        def defaults = DatabaseDefaults.builder().liquibaseHome(' /opt/liquibase ').liquibaseUrl(' ')
                .liquibaseJavaVersion(17).allowedTargets([' rd-db.bbh.com ', 'RD-DB.bbh.com', '', 'qc-db.bbh.com'])
                .snapshotRepositoryId(' ').build()

        expect:
        defaults == new DatabaseDefaults('/opt/liquibase', null, null, null, null, null, null, null, null, null,
                ['rd-db.bbh.com', 'qc-db.bbh.com'], null, null, null, null)
        DatabaseDefaults.builder().liquibaseJavaVersion(21).build().liquibaseJavaVersion() == 21
        DatabaseDefaults.NONE.allowedTargets() == []
    }

    def "the defaults render the tools, then the allowed databases, then the package repositories"() {
        given:
        def defaults = databaseDefaults().toBuilder().liquibaseUrl('https://tools.bbh.com/lb.zip').liquibaseSha256(SHA)
                .liquibaseJavaVersion(21).oracleJdbcUrl('https://tools.bbh.com/ojdbc11.jar').oracleJdbcSha256(SHA)
                .utplsqlHome('/opt/utplsql').build()
        def config = new ConfigTree()

        when:
        defaults.writeTo(config)

        then:
        config.toMap() == [tools   : [liquibase : [home: '/opt/liquibase/4.33.0', url: 'https://tools.bbh.com/lb.zip',
                                                   sha256: SHA, javaVersion: 21],
                                      oracleJdbc: [path: '/opt/oracle/ojdbc11.jar', url: 'https://tools.bbh.com/ojdbc11.jar',
                                                   sha256: SHA],
                                      utplsql   : [home: '/opt/utplsql', url: defaults.utplsqlUrl(),
                                                   sha256: defaults.utplsqlSha256()]],
                           database: [allowedTargets: ['dev-db.bbh.com', 'rd-db.bbh.com', 'qc-db.bbh.com'],
                                      package       : [snapshotRepository: [id : 'bbh-db-snapshots',
                                                                            url: 'https://tools.bbh.com/nexus/repository/db-snapshots'],
                                                       releaseRepository : [id : 'bbh-db-releases',
                                                                            url: 'https://tools.bbh.com/nexus/repository/db-releases']]]]
        (config.toMap().tools as Map).keySet() as List == ['liquibase', 'oracleJdbc', 'utplsql']
        (config.toMap().tools.liquibase as Map).keySet() as List == ['home', 'url', 'sha256', 'javaVersion']
        (config.toMap().database as Map).keySet() as List == ['allowedTargets', 'package']
    }

    def "no defaults render nothing"() {
        given:
        def config = new ConfigTree()

        when:
        DatabaseDefaults.NONE.writeTo(config)

        then:
        config.toMap() == [:]
    }

    def "each download needs its checksum (V29) and the allowed databases fit their column"() {
        given:
        def problems = new ValidationProblems()

        when:
        DatabaseDefaults.builder().liquibaseUrl('https://tools.bbh.com/lb.zip').oracleJdbcUrl('https://x/o.jar')
                .oracleJdbcSha256(SHA).utplsqlUrl('https://x/u.zip').allowedTargets(['a' * 4001]).build()
                .validate(problems.at('database'))

        then:
        problems.list()*.field() == ['database.liquibaseSha256', 'database.utplsqlSha256', 'database.allowedTargets']
        problems.list()*.message().first() ==
                'is required with the download URL: the 64 lower-case hexadecimal digits of the file'
    }

    def "a delivering pipeline misses #missing when the service sets #service and the defaults #defaults"() {
        given:
        def settings = DatabaseSettings.builder().groupId(service.groupId).snapshotRepositoryId(service.snapshotId)
                .snapshotRepositoryUrl(service.snapshotUrl).releaseRepositoryId(service.releaseId)
                .releaseRepositoryUrl(service.releaseUrl).build()
        def library = DatabaseDefaults.builder().snapshotRepositoryId(defaults.snapshotId)
                .snapshotRepositoryUrl(defaults.snapshotUrl).releaseRepositoryId(defaults.releaseId)
                .releaseRepositoryUrl(defaults.releaseUrl).build()

        expect:
        library.missingForDelivery(settings, release) == Optional.ofNullable(missing)

        where:
        service                                         | defaults                                     | release | missing
        [:]                                             | ALL                                          | true    | 'group ID'
        [groupId: 'g']                                  | [:]                                          | false   | 'snapshot repository ID'
        [groupId: 'g', snapshotId: 's']                 | [:]                                          | false   | 'snapshot repository URL'
        [groupId: 'g', snapshotId: 's']                 | [snapshotUrl: 'u']                           | false   | null
        [groupId: 'g', snapshotId: 's']                 | [snapshotUrl: 'u']                           | true    | 'release repository ID'
        [groupId: 'g']                                  | [snapshotId: 's', snapshotUrl: 'u', releaseId: 'r'] | true | 'release repository URL'
        [groupId: 'g', releaseUrl: 'u']                 | [snapshotId: 's', snapshotUrl: 'u', releaseId: 'r'] | true | null
        [groupId: 'g']                                  | ALL                                          | true    | null
        [groupId: 'g'] + ALL                            | [:]                                          | true    | null
    }

    static final Map ALL = [snapshotId: 's', snapshotUrl: 'u', releaseId: 'r', releaseUrl: 'v']
}
