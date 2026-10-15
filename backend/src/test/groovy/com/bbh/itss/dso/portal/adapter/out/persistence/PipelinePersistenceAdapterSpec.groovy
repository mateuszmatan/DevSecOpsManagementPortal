package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE
import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.REVOKED
import static com.bbh.itss.dso.portal.domain.pipeline.Pipeline.REPLACED_REASON
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:pipeline-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import([PipelinePersistenceAdapter, ProductPersistenceAdapter])
class PipelinePersistenceAdapterSpec extends Specification {

    static final Instant ISSUED = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant REISSUED = Instant.parse('2026-10-02T09:30:00Z')
    static final Instant USED = Instant.parse('2026-10-03T10:15:00Z')

    @Autowired
    PipelinePersistenceAdapter adapter

    @Autowired
    ProductPersistenceAdapter products

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    int issued
    KeyGenerator generator = { -> "key-${++issued}".toString() } as KeyGenerator

    Product cert

    def setup() {
        cert = products.save(Product.create(details(), account(), [draft('gui'), draft('api')], products))
    }

    def "a new pipeline is stored with its settings and its first active key"() {
        when:
        def saved = adapter.save(Pipeline.create(ref(cert, 0), FULL,
                pipelineSettings(agentLabels: ['linux', 'docker'], jenkinsJob: 'CERT/gui', description: 'Main',
                        extendedPipelineJob: 'ignored'), generator, ISSUED))
        entities.clear()
        def loaded = adapter.load(saved.id()).get()

        then:
        saved.version() == 0
        saved.createdAt() != null
        loaded.service() == ref(cert, 0)
        loaded.type() == FULL
        loaded.settings() == pipelineSettings(agentLabels: ['linux', 'docker'], jenkinsJob: 'CERT/gui',
                description: 'Main')
        loaded.keys() == saved.keys()
        loaded.keys()*.value() == ['key-1']
        loaded.keys()[0].status() == ACTIVE
        loaded.keys()[0].issuedAt() == ISSUED
        jdbc.queryForMap('SELECT PIPELINE_TYPE, AGENT_LABELS, EXTENDED_PIPELINE_JOB, JENKINS_JOB FROM DSO_PIPELINE') ==
                [PIPELINE_TYPE: 'FULL', AGENT_LABELS: 'linux,docker', EXTENDED_PIPELINE_JOB: null,
                 JENKINS_JOB: 'CERT/gui']
    }

    def "a reconfigured pipeline keeps its keys"() {
        given:
        def stored = stored(ref(cert, 0), SECURITY)
        def pipeline = adapter.load(stored.id()).get()

        when:
        pipeline.reconfigure(stored.version(), SECURITY, pipelineSettings(agentLabels: ['windows'],
                extendedPipelineJob: 'CERT/extended'))
        def saved = adapter.save(pipeline)
        entities.clear()

        then:
        def loaded = adapter.load(stored.id()).get()
        saved.version() == stored.version() + 1
        loaded.settings().agentLabels() == ['windows']
        loaded.settings().extendedPipelineJob() == 'CERT/extended'
        loaded.keys()*.id() == stored.keys()*.id()
    }

    def "a new key replaces the active key, which stays in the history as revoked and is still found"() {
        given:
        def stored = stored(ref(cert, 0))
        def pipeline = adapter.loadForUpdate(stored.id()).get()

        when:
        pipeline.issueKey(generator, REISSUED)
        def saved = adapter.save(pipeline)
        entities.clear()
        def loaded = adapter.load(stored.id()).get()

        then:
        loaded.keys() == saved.keys()
        loaded.keys()*.value() == ['key-2', 'key-1']
        loaded.keys()*.status() == [ACTIVE, REVOKED]
        loaded.keys()[1].id() == stored.keys()[0].id()
        loaded.keys()[1].revokedAt() == REISSUED
        loaded.keys()[1].revokeReason() == REPLACED_REASON
        loaded.keys()[0].issuedAt() == REISSUED
        ['key-1', 'key-2'].collect { adapter.findKey(it).get() }*.pipelineId() == [stored.id()] * 2
        adapter.findKey('key-2').get().key() == saved.keys()[0]
        adapter.findKey('key-3').empty
        adapter.load(999_999L).empty
        adapter.loadForUpdate(999_999L).empty
    }

    def "a key use is recorded on active keys only and never changes their status"() {
        given:
        def pipeline = stored(ref(cert, 0))
        pipeline.issueKey(generator, REISSUED)
        def saved = adapter.save(pipeline)
        def (active, revoked) = saved.keys()*.id()

        when:
        def recorded = [active, revoked, 999_999L].collect { adapter.recordKeyUse(it, USED) }
        entities.clear()
        def loaded = adapter.load(saved.id()).get()

        then:
        recorded == [true, false, false]
        loaded.keys()*.lastUsedAt() == [USED, null]
        loaded.keys()*.status() == [ACTIVE, REVOKED]
        loaded.version() == saved.version()
    }

    def "a pipeline never holds a second active key"() {
        given:
        def stored = stored(ref(cert, 0))
        entities.flush()

        when:
        jdbc.update("""INSERT INTO DSO_PIPELINE_KEY (PIPELINE_ID, KEY_VALUE, STATUS, ISSUED_AT)
                       VALUES (?, 'key-second', 'ACTIVE', CURRENT_TIMESTAMP)""", stored.id())

        then:
        thrown(DataIntegrityViolationException)
    }

    def "a revoked key disables the pipeline, which keeps any number of revoked keys beside its active key"() {
        given:
        def pipeline = stored(ref(cert, 0))
        pipeline.issueKey(generator, REISSUED)
        pipeline = adapter.save(pipeline)
        pipeline.revokeActiveKey('Service retired', USED)
        adapter.save(pipeline)
        entities.clear()

        when:
        def revoked = adapter.load(pipeline.id()).get()
        def latest = revoked.keys()[0]
        def enabled = revoked.enabled
        revoked.issueKey(generator, USED)
        adapter.save(revoked)
        entities.clear()

        then:
        !enabled
        latest.revokeReason() == 'Service retired'
        latest.revokedAt() == USED
        adapter.load(pipeline.id()).get().keys()*.status().countBy { it } == [(ACTIVE): 1, (REVOKED): 2]
        jdbc.queryForObject("SELECT COUNT(*) FROM DSO_PIPELINE_KEY WHERE STATUS = 'ACTIVE'", Integer) == 1
    }

    def "pipelines are listed and counted per product, per department and overall in service and type order"() {
        given:
        def other = products.save(Product.create(details(code: 'ABC', name: 'Abacus', departmentId: 5L), account(),
                [draft('core')], products))
        def apiSast = stored(ref(cert, 1), SAST)
        def guiSecurity = stored(ref(cert, 0), SECURITY)
        def guiSast = stored(ref(cert, 0), SAST)
        def guiNexusIq = stored(ref(cert, 0), NEXUS_IQ)
        def guiFull = stored(ref(cert, 0))
        def core = stored(ref(other, 0))
        apiSast.revokeActiveKey('Retired', REISSUED)
        adapter.save(apiSast)
        entities.clear()

        expect:
        adapter.findByProductId(cert.id())*.id() ==
                [guiFull.id(), guiNexusIq.id(), guiSast.id(), guiSecurity.id(), apiSast.id()]
        adapter.findByProductId(999_999L).empty
        adapter.findAll()*.id() ==
                [core.id(), guiFull.id(), guiNexusIq.id(), guiSast.id(), guiSecurity.id(), apiSast.id()]
        adapter.findAll()*.service() == [ref(other, 0)] + [ref(cert, 0)] * 4 + [ref(cert, 1)]
        adapter.findByDepartmentId(cert.departmentId())*.id() ==
                [guiFull.id(), guiNexusIq.id(), guiSast.id(), guiSecurity.id(), apiSast.id()]
        adapter.findByDepartmentId(5L)*.id() == [core.id()]
        adapter.findByDepartmentId(999_999L).empty
        adapter.load(guiNexusIq.id()).get().type() == NEXUS_IQ
        jdbc.queryForObject('SELECT PIPELINE_TYPE FROM DSO_PIPELINE WHERE ID = ?', String, guiNexusIq.id()) ==
                'NEXUS_IQ'
        adapter.existsForService(cert.services()[0].id(), FULL)
        adapter.existsForService(cert.services()[0].id(), NEXUS_IQ)
        !adapter.existsForService(cert.services()[1].id(), NEXUS_IQ)
        !adapter.existsForService(cert.services()[1].id(), FULL)
        adapter.pipelinesPerProduct() == [(cert.id()): 5L, (other.id()): 1L]
        adapter.activePipelinesPerProduct() == [(cert.id()): 4L, (other.id()): 1L]
    }

    def "a tag is shared when pipelines of several services write under it"() {
        given:
        def metrics = MetricsSettings.of(true, 'CertScanner', 'test')
        def tagged = products.save(Product.create(details(code: 'TAG', name: 'Tagged'), account(), [
                new ServiceDraft(null, 'gui', null, settings(metrics: metrics)),
                new ServiceDraft(null, 'api', null, settings(metrics: metrics)),
                draft('batch')], products))
        stored(ref(tagged, 0))
        stored(ref(tagged, 0), SAST)
        stored(ref(tagged, 1))
        stored(ref(tagged, 2))
        stored(ref(cert, 0))

        expect:
        adapter.sharedMetricsTags() == [new MetricsTag('CertScanner', 'test')] as Set
    }

    def "a pipeline #change is not written"() {
        given:
        def stored = stored(ref(cert, 0))
        def stale = Pipeline.restore(stored.id(), stored.service(), FULL, pipelineSettings(agentLabels: ['stale']),
                stored.keys(), stored.version() + versionShift, stored.createdAt(), stored.updatedAt())
        if (deleted) {
            adapter.delete(stored.id())
        }

        when:
        adapter.save(service ? Pipeline.create(service, FULL, pipelineSettings(), generator, ISSUED) : stale)

        then:
        def problem = thrown(failure)
        problem.message.contains(message)

        where:
        change                                | versionShift | deleted | service                      || failure                | message
        'read at another version than stored' | 1            | false   | null                         || IllegalStateException  | 'changed by someone else'
        'deleted in the meantime'             | 0            | true    | null                         || IllegalStateException  | 'changed by someone else'
        'of a service that was never stored'  | 0            | false   | new ServiceRef(1L, 999_999L) || NoSuchElementException | '999999'
    }

    def "deleting a pipeline removes its keys"() {
        given:
        def stored = stored(ref(cert, 0))
        stored.issueKey(generator, REISSUED)
        adapter.save(stored)

        when:
        adapter.delete(stored.id())
        adapter.delete(999_999L)
        entities.flush()

        then:
        adapter.load(stored.id()).empty
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE', Integer) == 0
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_KEY', Integer) == 0
    }

    private Pipeline stored(ServiceRef service, PipelineType type = FULL) {
        def saved = adapter.save(Pipeline.create(service, type, pipelineSettings(), generator, ISSUED))
        entities.clear()
        saved
    }

    private static ServiceDraft draft(String name) {
        new ServiceDraft(null, name, null, settings())
    }

    private static ServiceRef ref(Product product, int service) {
        new ServiceRef(product.id(), product.services()[service].id())
    }
}
