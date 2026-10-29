package com.bbh.itss.dso.portal.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:product-appscan-key;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class ProductAppScanKeyMigrationSpec extends MigrationSpecification {

    def "a product without an AppScan key is stored after 018, gets a placeholder key on rollback and none again after update"() {
        given:
        liquibase.update('')
        insert('DSO_PRODUCT', [CODE: 'CERT', NAME: 'CertScanner'])
        insert('DSO_PRODUCT', [CODE: 'PAY', NAME: 'Payments Hub', ASOC_KEY_ID: 'bbh_key-id'])

        expect:
        nullable('DSO_PRODUCT', 'ASOC_KEY_ID')
        keyIds() == [CERT: null, PAY: 'bbh_key-id']

        when:
        rollBackSince('018-')

        then:
        !nullable('DSO_PRODUCT', 'ASOC_KEY_ID')
        keyIds() == [CERT: 'Not set', PAY: 'bbh_key-id']

        when:
        jdbc.update('UPDATE DSO_PRODUCT SET ASOC_KEY_ID = NULL WHERE CODE = ?', 'PAY')

        then:
        thrown(DataIntegrityViolationException)

        when:
        liquibase.update('')
        jdbc.update('UPDATE DSO_PRODUCT SET ASOC_KEY_ID = NULL WHERE CODE = ?', 'CERT')

        then:
        nullable('DSO_PRODUCT', 'ASOC_KEY_ID')
        keyIds() == [CERT: null, PAY: 'bbh_key-id']
    }

    private Map<String, String> keyIds() {
        jdbc.queryForList('SELECT CODE, ASOC_KEY_ID FROM DSO_PRODUCT ORDER BY CODE')
                .collectEntries { [it.CODE, it.ASOC_KEY_ID] }
    }
}
