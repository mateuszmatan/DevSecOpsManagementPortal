package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.adapter.in.web.GlobalSettingsDto.DatabaseDefaultsDto
import com.bbh.itss.dso.portal.adapter.RecordMapper
import com.bbh.itss.dso.portal.domain.settings.DatabaseDefaults
import com.bbh.itss.dso.portal.support.Fixtures
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ABSOLUTE_MESSAGE
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ARCHIVE_MESSAGE
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.JAR_MESSAGE
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.SHA256_MESSAGE
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.TARGET_MESSAGE
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.TOOL_URL_MESSAGE
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ZIP_MESSAGE
import static com.bbh.itss.dso.portal.support.ApiJson.databaseDefaults
import static jakarta.validation.Validation.buildDefaultValidatorFactory

class GlobalSettingsDtoSpec extends Specification {

    @Shared
    Validator validator = buildDefaultValidatorFactory().validator

    def "the Library defaults (Liquibase database changes) of the regression settings are valid and map to the domain"() {
        given:
        def dto = defaults()

        expect:
        validator.validate(dto).isEmpty()
        RecordMapper.map(dto, DatabaseDefaults) == Fixtures.databaseDefaults()
        validator.validate(RecordMapper.map(DatabaseDefaults.NONE, DatabaseDefaultsDto)).isEmpty()
    }

    def "bean validation rejects #description and says what it expects"() {
        expect:
        validator.validate(defaults(change)).collect { [it.propertyPath.toString(), it.message] }.sort() ==
                messages.collect { [change.keySet()[0] + suffix, it] }.sort()

        where:
        description                  | change                                                 | suffix               || messages
        'a relative Liquibase home'  | [liquibaseHome: 'opt/liquibase']                       | ''                   || [ABSOLUTE_MESSAGE]
        'a 501-character home'       | [utplsqlHome: '/' + 'u' * 500]                         | ''                   || [ABSOLUTE_MESSAGE]
        'a driver that is no jar'    | [oracleJdbcPath: '/opt/oracle/ojdbc11.zip']            | ''                   || [JAR_MESSAGE]
        'a download over http'       | [liquibaseUrl: 'http://tools.bbh.com/lb.zip']          | ''                   || [TOOL_URL_MESSAGE]
        'a Liquibase jar download'   | [liquibaseUrl: 'https://tools.bbh.com/lb.jar']         | ''                   || [ARCHIVE_MESSAGE]
        'a zipped driver download'   | [oracleJdbcUrl: 'https://tools.bbh.com/ojdbc.zip']     | ''                   || [JAR_MESSAGE]
        'a utPLSQL tarball download' | [utplsqlUrl: 'https://tools.bbh.com/u.tar.gz']         | ''                   || [ZIP_MESSAGE]
        'an upper-case checksum'     | [utplsqlSha256: 'A' * 64]                              | ''                   || [SHA256_MESSAGE]
        'Java 11'                    | [liquibaseJavaVersion: 11]                             | ''                   || ['must be greater than or equal to 17']
        'an allowed URL'             | [allowedTargets: ['jdbc:oracle:thin:@//rd-db:1521/X']] | '[0].<list element>' || [TARGET_MESSAGE]
        'a blank allowed database'   | [allowedTargets: [' ']]                                | '[0].<list element>' || ['must not be blank', TARGET_MESSAGE]
    }

    def "an allowed database may be a host or a host with its service, with * for any run"() {
        expect:
        validator.validate(defaults(allowedTargets: ['rd-db.bbh.com', '*.qc.bbh.com', 'dev-db.bbh.com/ORDERS_*'])).isEmpty()
    }

    private static DatabaseDefaultsDto defaults(Map change = [:]) {
        new DatabaseDefaultsDto(*(databaseDefaults() + change).with { values ->
            DatabaseDefaultsDto.recordComponents*.name.collect { values[it] }
        })
    }
}
