package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Product
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:product-appscan-key;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import(ProductPersistenceAdapter)
class ProductAppScanKeyMigrationSpec extends MigrationSpecification {

    @Autowired
    ProductPersistenceAdapter products

    def "a product without an AppScan key is stored after 018, gets a placeholder key on rollback and none again after update"() {
        given:
        liquibase.update('')
        def keyless = store(Product.create(details(), null, [], products))
        def keyed = store(Product.create(details(code: 'PAY', name: 'Payments Hub'), account(), [], products))

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
        inTransaction { products.load(keyless.id()).get().appScanAccount() } == null
        inTransaction { products.load(keyed.id()).get().appScanAccount() } == account()
    }

    private Product store(Product product) {
        inTransaction { products.save(product) }
    }

    private Map<String, String> keyIds() {
        jdbc.queryForList('SELECT CODE, ASOC_KEY_ID FROM DSO_PRODUCT ORDER BY CODE')
                .collectEntries { [it.CODE, it.ASOC_KEY_ID] }
    }
}
