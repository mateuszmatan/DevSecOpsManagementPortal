package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import groovy.json.JsonSlurper
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.DatabaseTarget.QC
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseTarget.RD
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseTarget.TEST

class DatabaseSettingsSpec extends Specification {

    static final DatabaseSettings VALID = DatabaseSettings.builder().changelogFile('db/changelog-master.xml').build()
    static final String RD_URL = 'jdbc:oracle:thin:@//rd-db.bbh.com:1521/ORDERS_RD'
    static final String TEST_URL = 'jdbc:oracle:thin:@//dev-db.bbh.com:1521/ORDERS_CI'

    def "#field set to #value is stored as #stored"() {
        expect:
        DatabaseSettings.builder()."$field"(value).build()."$field"() == stored

        where:
        field                   | value                     | stored
        'changelogFile'         | '  '                      | null
        'groupId'               | ' com.bbh.orders '        | 'com.bbh.orders'
        'searchPath'            | './src/main/resources/'   | 'src/main/resources'
        'searchPath'            | '.'                       | null
        'oracleVersion'         | '19.0'                    | null
        'oracleVersion'         | ' 23.4 '                  | '23.4'
        'contexts'              | '!test'                   | null
        'contexts'              | 'rd'                      | 'rd'
        'requireRollback'       | false                     | null
        'requireRollback'       | true                      | true
        'testReset'             | false                     | null
        'utplsql'               | false                     | null
        'tag'                   | true                      | null
        'tag'                   | false                     | false
        'compileCheck'          | true                      | null
        'recompile'             | true                      | null
        'nexusDelivery'         | true                      | null
        'nexusDelivery'         | false                     | false
        'lockWaitMinutes'       | 5                         | null
        'lockWaitMinutes'       | 10                        | 10
        'utplsqlTimeoutMinutes' | 30                        | null
        'parameters'            | [' a=1 ', ' ', 'b=2']     | ['a=1', 'b=2']
        'ignoreObjects'         | null                      | []
        'ignoreFiles'           | ['db/legacy/old.sql', '']   | ['db/legacy/old.sql']
    }

    def "a database keeps only what the library does not use anyway: test for the test database and 30 minutes"() {
        given:
        def target = DatabaseTargetSettings.builder().url(" $RD_URL ").contexts('test').timeoutMinutes(30)
                .parameters([' a=1 ', '']).build()

        expect:
        target.url() == RD_URL
        target.parameters() == ['a=1']
        target.withoutDefaults(TEST) == new DatabaseTargetSettings(RD_URL, null, null, null, null, null, ['a=1'], null)
        target.withoutDefaults(RD).contexts() == 'test'
        target.withoutDefaults(RD).timeoutMinutes() == null
        DatabaseTargetSettings.builder().contexts('test').build().withoutDefaults(TEST).empty
        !DatabaseTargetSettings.builder().parameters(['x']).build().empty
    }

    def "an artifact ID equal to the service name is dropped, as the library names the package after the project"() {
        given:
        def named = VALID.withArtifactId('orders-db')

        expect:
        named.withDefaultArtifact('orders-db').artifactId() == null
        named.withDefaultArtifact('orders').artifactId() == 'orders-db'
        VALID.withDefaultArtifact('orders-db').is(VALID)
    }

    def "a minimal block renders only the changelog"() {
        expect:
        render(VALID) == [database: [changelogFile: 'db/changelog-master.xml']]
    }

    def "a full block renders every key in the order of the library's table"() {
        given:
        def full = new DatabaseSettings('db/changelog-master.xml', 'src/main/resources', '23.4', 'APP', 'LB', 'rd, !test',
                'core', ['app_role=orders_app_role'], true, false, 10, false, false, ['PKG_LEGACY'], ['db/legacy/old.sql'],
                'com.bbh.orders', 'orders-schema', null, '/opt/maven', 'snapshots', 'https://nexus.bbh.com/snapshots',
                'releases', 'https://nexus.bbh.com/releases', true, true, 'orders', 'APP_OWNER', 'db/code', 'db/test', 60,
                'db', '5.0.1.3006')
        def targets = [(QC)  : DatabaseTargetSettings.builder().url(RD_URL.replace('rd', 'qc')).credentialsId('qc')
                .contexts('qc').labels('core').defaultSchema('APP').liquibaseSchema('LB').parameters(['a=1'])
                .timeoutMinutes(45).build(),
                       (TEST): DatabaseTargetSettings.builder().url(TEST_URL).credentialsId('test').build()]

        when:
        def config = render(full, targets)

        then:
        config == [database: [changelogFile  : 'db/changelog-master.xml', searchPath: 'src/main/resources',
                              oracleVersion  : '23.4', defaultSchema: 'APP', liquibaseSchema: 'LB', contexts: 'rd, !test',
                              labels         : 'core', parameters: [app_role: 'orders_app_role'], requireRollback: true,
                              tag            : false, lockWaitMinutes: 10,
                              compileCheck   : [enabled: false, recompile: false, ignoreObjects: ['PKG_LEGACY']],
                              checks         : [ignoreFiles: ['db/legacy/old.sql']],
                              package        : [groupId           : 'com.bbh.orders', artifactId: 'orders-schema',
                                                mvnPath           : '/opt/maven',
                                                snapshotRepository: [id: 'snapshots', url: 'https://nexus.bbh.com/snapshots'],
                                                releaseRepository : [id: 'releases', url: 'https://nexus.bbh.com/releases']],
                              test           : [url    : TEST_URL, credentialsId: 'test', reset: true,
                                                utplsql: [enabled : true, paths: 'orders', owner: 'APP_OWNER',
                                                          sourcePath: 'db/code', testPath: 'db/test', timeoutMinutes: 60]],
                              qc             : [url           : RD_URL.replace('rd', 'qc'), credentialsId: 'qc', contexts: 'qc',
                                                labels        : 'core', defaultSchema: 'APP', liquibaseSchema: 'LB',
                                                parameters    : [a: '1'], timeoutMinutes: 45]],
                   tools   : [sonar: [sources: 'db', sonarScannerVersion: '5.0.1.3006']]]
        (config.database as Map).keySet() as List == ['changelogFile', 'searchPath', 'oracleVersion', 'defaultSchema',
                                                      'liquibaseSchema', 'contexts', 'labels', 'parameters',
                                                      'requireRollback', 'tag', 'lockWaitMinutes', 'compileCheck',
                                                      'checks', 'package', 'test', 'qc']
        (config.database.package as Map).keySet() as List == ['groupId', 'artifactId', 'mvnPath', 'snapshotRepository',
                                                              'releaseRepository']
        (config.database.test as Map).keySet() as List == ['url', 'credentialsId', 'reset', 'utplsql']
    }

    def "without the delivery the package names only its artifact, and the utPLSQL keys need utPLSQL"() {
        given:
        def settings = VALID.toBuilder().nexusDelivery(false).groupId('com.bbh.orders').artifactId('orders-schema')
                .mvnPath('/opt/maven').snapshotRepositoryId('snapshots').releaseRepositoryUrl('https://nexus.bbh.com/r')
                .utplsqlPaths('orders').utplsqlSourcePath('db/code').build()

        expect:
        render(settings).database == [changelogFile: 'db/changelog-master.xml',
                                      package      : [artifactId: 'orders-schema', nexusDelivery: false]]
    }

    def "a valid block has no problem"() {
        expect:
        problems(VALID, [(TEST): target(TEST_URL), (RD): target(RD_URL)]) == []
        problems(VALID.toBuilder().changelogFile('x.xml').build(), [:], 'orders-db') == []
        problems(VALID.toBuilder().changelogFile('x.xml').searchPath('db').build()) == []
    }

    def "the changelog is required (V1) and needs a folder below a repository root search root (V35)"() {
        expect:
        problems(DatabaseSettings.NONE) == [problem('database.changelogFile',
                'is required for the build tool SQL and PL/SQL (Liquibase)')]
        problems(VALID.toBuilder().changelogFile('x.xml').build())*.field() == ['database.changelogFile']
    }

    def "#url is refused as #problem (V5, V6)"() {
        expect:
        problems(VALID, [(RD): target(url)]) == [problem('databaseTargets[RD].url', message)]

        where:
        row << CASES.urls.findAll { it.problem }
        url = row.url as String
        problem = row.problem
        message = problem == 'SECRET' ? SECRET_URL : FORM_URL
    }

    def "a database needs both its URL and its credentials ID (V7, V9)"() {
        expect:
        problems(VALID, [(RD): DatabaseTargetSettings.builder().url(RD_URL).build(),
                         (QC): DatabaseTargetSettings.builder().credentialsId('orders-db-qc').contexts('!test').build()]) ==
                [problem('databaseTargets[RD].credentialsId',
                        'is required with the JDBC URL: the ID of the Jenkins credentials of the database user'),
                 problem('databaseTargets[QC].credentialsId', 'is set without a JDBC URL')]
    }

    def "the test database must be a database of its own (V12)"() {
        expect:
        problems(VALID, [(TEST): target(TEST_URL), (QC): target(TEST_URL.toLowerCase())]) == [problem(
                'databaseTargets[TEST].url', 'is also the URL of the RD or QC database: the test database receives the'
                + ' test packages and must be a database of its own')]
    }

    def "QC may not deploy the test changesets: QC contexts #qc, service contexts #service (V13)"() {
        given:
        def settings = VALID.toBuilder().contexts(service).build()
        def targets = qc == null ? [:] : [(QC): DatabaseTargetSettings.builder().contexts(qc).build()]

        expect:
        problems(settings, targets)*.field() == fields

        where:
        qc      | service | fields
        null    | null    | []
        null    | 'rd'    | []
        null    | 'test'  | ['database.contexts']
        'test'  | null    | ['databaseTargets[QC].contexts']
        '!test' | 'test'  | []
        'qc'    | '!test' | []
        'qc,test' | null  | ['databaseTargets[QC].contexts']
    }

    def "utPLSQL and the test reset need the test database (V17, V41)"() {
        given:
        def settings = VALID.toBuilder().utplsql(true).testReset(true).build()

        expect:
        problems(settings) == [problem('database.utplsql', 'needs the JDBC URL of the test database'),
                               problem('database.testReset', 'needs the JDBC URL of the test database')]
        problems(settings, [(TEST): target(TEST_URL)]) == []
    }

    def "changelog parameters #lines are refused at the service and at a database (V37, V38, V39)"() {
        expect:
        problems(VALID.toBuilder().parameters(lines).build())*.message() == [message]
        problems(VALID, [(RD): DatabaseTargetSettings.builder().url(RD_URL).credentialsId('orders-db-rd')
                .parameters(lines).build()]) ==
                [problem('databaseTargets[RD].parameters', message)]

        where:
        lines                                | message
        ['App=v4lue']                        | NAME
        ['novalue']                          | NAME
        (1..51).collect { "p$it=v".toString() } | NAME
        ['db_password=s3cr3t']               | SECRET
        ['token.id=v4lue']                   | SECRET
        ['x=${env}']                         | VALUE
        ['x= v4lue']                         | VALUE
        ['x=välue']                     | VALUE
    }

    def "the lists must fit the 4000 bytes of their column"() {
        given:
        def long4001 = ['a' * 4001]

        expect:
        problems(VALID.toBuilder().ignoreObjects(long4001).ignoreFiles(long4001).build())*.field() ==
                ['database.ignoreObjects', 'database.ignoreFiles']
        problems(VALID.toBuilder().ignoreObjects(['a' * 4000]).build()) == []
    }

    def "no message repeats a URL, a host, a parameter value or a credentials ID"() {
        given:
        def settings = VALID.toBuilder().changelogFile('orders.xml').parameters(['App=v4lue', 'db_password=s3cr3t'])
                .utplsql(true).contexts('t3st-ctx, test').build()
        def secret = 'jdbc:oracle:thin:scott/tiger@//rd-db.bbh.com:1521/ORDERS_RD'
        def targets = [(RD)  : DatabaseTargetSettings.builder().url(secret).parameters(['x=${env}']).build(),
                       (QC)  : DatabaseTargetSettings.builder().credentialsId('cred-qc-42').build(),
                       (TEST): DatabaseTargetSettings.builder().url('jdbc:oracle:thin:@ORDERS_CI').build()]

        when:
        def messages = problems(settings, targets)*.message()

        then:
        messages.size() == 10
        ['orders.xml', 'v4lue', 's3cr3t', 'scott', 'tiger', 'rd-db', 'bbh.com', 'ORDERS', 'cred-qc-42', 't3st-ctx',
         '${env}'].every { value -> messages.every { !it.contains(value) } }
    }

    static final Map CASES = new JsonSlurper().parse(DatabaseSettingsSpec.getResource('/database-rules-cases.json')) as Map
    static final String SECRET_URL = 'must not contain a user or a password: keep them in the Jenkins credentials of' +
            ' the credentials ID'
    static final String FORM_URL = 'must be an Oracle thin URL: jdbc:oracle:thin:@//host:1521/SERVICE, @host:1521:SID,' +
            ' @tcps://host:2484/SERVICE?ssl_server_dn_match=true or @(DESCRIPTION=...)'
    static final String NAME = 'each line must be name=value, where the name starts with a lower-case letter and holds' +
            ' only lower-case letters, digits, "_" and "." (at most 64), and at most 50 lines'
    static final String SECRET = 'holds a name that looks like a secret: changelog parameters are written into the' +
            ' package; keep secrets in Jenkins credentials'
    static final String VALUE = 'holds a value that is not printable ASCII of at most 500 characters, or that starts or' +
            ' ends with a blank, or holds "${"'

    static DatabaseTargetSettings target(String url) {
        DatabaseTargetSettings.builder().url(url).credentialsId('orders-db').build()
    }

    static Map render(DatabaseSettings settings, Map<DatabaseTarget, DatabaseTargetSettings> targets = [:]) {
        def config = new ConfigTree()
        settings.writeTo(config, targets.sort { it.key })
        config.toMap()
    }

    static List<FieldProblem> problems(DatabaseSettings settings, Map<DatabaseTarget, DatabaseTargetSettings> targets = [:],
                                       String sourceDir = '.') {
        def problems = new ValidationProblems()
        settings.validate(problems, targets.sort { it.key }, sourceDir)
        problems.list()
    }

    static FieldProblem problem(String field, String message) {
        new FieldProblem(field, message)
    }
}
