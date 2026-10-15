package com.bbh.itss.dso.portal.adapter.out.persistence

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA

class ScannerListConverterSpec extends Specification {

    def "the release gate scanners are stored as #column"() {
        expect:
        new ScannerListConverter().convertToDatabaseColumn(scanners) == column

        where:
        scanners                || column
        null                    || null
        []                      || null
        [SAST]                  || 'SAST'
        [SAST, NEXUS_IQ, DAST]  || 'SAST,NEXUS_IQ,DAST'
    }

    def "the stored column #column is read as #scanners"() {
        expect:
        new ScannerListConverter().convertToEntityAttribute(column) == scanners

        where:
        column               || scanners
        null                 || []
        ''                   || []
        '   '                || []
        'SCA'                || [SCA]
        'SAST, NEXUS_IQ,DAST' || [SAST, NEXUS_IQ, DAST]
    }
}
