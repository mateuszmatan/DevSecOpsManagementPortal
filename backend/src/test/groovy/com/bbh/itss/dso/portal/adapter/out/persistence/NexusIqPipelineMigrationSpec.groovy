package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:nexus-iq-pipeline;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([PipelinePersistenceAdapter, ProductPersistenceAdapter])
class NexusIqPipelineMigrationSpec extends MigrationSpecification {

    static final Instant ISSUED = Instant.parse('2026-10-08T08:00:00Z')

    @Autowired
    PipelinePersistenceAdapter pipelines

    @Autowired
    ProductPersistenceAdapter products

    int issued
    KeyGenerator generator = { -> "key-${++issued}".toString() } as KeyGenerator

    def "a Nexus IQ pipeline is stored after 015, removed with its key on rollback and accepted again after update"() {
        given:
        liquibase.update('')
        def product = inTransaction { products.save(Product.create(details(), account(),
                [new ServiceDraft(null, 'gui', null, settings())], products)) }
        def gui = new ServiceRef(product.id(), product.services()[0].id())
        def nexusIq = store(gui, NEXUS_IQ)
        long sast = store(gui, SAST).id()

        expect:
        types() == ['NEXUS_IQ', 'SAST']
        keysOf(nexusIq.id()) == 1

        when:
        liquibase.rollback(executedSince('015-'), '')

        then:
        types() == ['SAST']
        keysOf(nexusIq.id()) == 0
        keysOf(sast) == 1

        when:
        insert(gui, 'NEXUS_IQ')

        then:
        thrown(DataIntegrityViolationException)

        when:
        liquibase.update('')
        insert(gui, 'NEXUS_IQ')

        then:
        types() == ['NEXUS_IQ', 'SAST']
        inTransaction { pipelines.findAll()*.type() } == [NEXUS_IQ, SAST]
    }

    private Pipeline store(ServiceRef service, PipelineType type) {
        inTransaction { pipelines.save(Pipeline.create(service, type, pipelineSettings(), generator, ISSUED)) }
    }

    private void insert(ServiceRef service, String type) {
        jdbc.update('''INSERT INTO DSO_PIPELINE (SERVICE_ID, PIPELINE_TYPE, AGENT_LABELS, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'linux-agent', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''', service.serviceId(), type)
    }

    private List<String> types() {
        jdbc.queryForList('SELECT PIPELINE_TYPE FROM DSO_PIPELINE ORDER BY PIPELINE_TYPE', String)
    }

    private int keysOf(long pipelineId) {
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_KEY WHERE PIPELINE_ID = ?', Integer, pipelineId)
    }
}
