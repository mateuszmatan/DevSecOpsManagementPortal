package com.bbh.itss.dso.portal.adapter.out.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.support.Fixtures.APP_ID
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:nexus-iq-pipeline;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import(PipelinePersistenceAdapter)
class NexusIqPipelineMigrationSpec extends MigrationSpecification {

    @Autowired
    PipelinePersistenceAdapter pipelines

    def "a Nexus IQ pipeline is stored after 015, removed with its key on rollback and accepted again after update"() {
        given:
        liquibase.update('')
        long product = insert('DSO_PRODUCT', [CODE: 'CERT', NAME: 'CertScanner'])
        long gui = insert('DSO_SERVICE', [PRODUCT_ID: product, NAME: 'gui', BUILD_TOOL: 'GRADLE', SOURCE_DIR: '.',
                                          DEPLOY_TARGET: 'VM', APPSCAN_APP_ID: APP_ID, INFLUX_PROJECT: 'CERT-gui'])
        long nexusIq = store(gui, 'NEXUS_IQ')
        long sast = store(gui, 'SAST')

        expect:
        types() == ['NEXUS_IQ', 'SAST']
        keysOf(nexusIq) == 1

        when:
        liquibase.rollback(executedSince('015-'), '')

        then:
        types() == ['SAST']
        keysOf(nexusIq) == 0
        keysOf(sast) == 1

        when:
        pipeline(gui, 'NEXUS_IQ')

        then:
        thrown(DataIntegrityViolationException)

        when:
        liquibase.update('')
        pipeline(gui, 'NEXUS_IQ')
        upgradeToCurrentSchema()

        then:
        types() == ['NEXUS_IQ', 'SAST']
        inTransaction { pipelines.findAll()*.type() } == [NEXUS_IQ, SAST]
    }

    /** Stores a pipeline with its active key, as a pipeline the portal created before the rollback. */
    private long store(long serviceId, String type) {
        long id = pipeline(serviceId, type)
        jdbc.update("""INSERT INTO DSO_PIPELINE_KEY (PIPELINE_ID, KEY_VALUE, STATUS, ISSUED_AT)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP)""", id, "key-$type".toString())
        id
    }

    private long pipeline(long serviceId, String type) {
        insert('DSO_PIPELINE', [SERVICE_ID: serviceId, PIPELINE_TYPE: type, AGENT_LABELS: 'linux-agent'])
    }

    /** The entities map the current schema, which only the master changelog reaches past 026. */
    private void upgradeToCurrentSchema() {
        liquibase.close()
        liquibase = changelog('db/changelog/db.changelog-master.yaml')
        liquibase.update('')
    }

    private List<String> types() {
        jdbc.queryForList('SELECT PIPELINE_TYPE FROM DSO_PIPELINE ORDER BY PIPELINE_TYPE', String)
    }

    private int keysOf(long pipelineId) {
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_KEY WHERE PIPELINE_ID = ?', Integer, pipelineId)
    }
}
