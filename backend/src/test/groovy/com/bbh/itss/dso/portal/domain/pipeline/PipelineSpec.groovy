package com.bbh.itss.dso.portal.domain.pipeline

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE
import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.REVOKED
import static com.bbh.itss.dso.portal.domain.pipeline.Pipeline.REPLACED_REASON
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.support.Fixtures.activeKey
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey

class PipelineSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-05T10:15:30.123456Z')
    static final Instant LATER = Instant.parse('2026-10-05T11:00:00Z')
    static final ServiceRef GUI = new ServiceRef(1, 10)

    int issued
    KeyGenerator generator = { -> "key-${++issued}".toString() } as KeyGenerator

    def "a new pipeline of a service starts with one active key"() {
        when:
        def created = Pipeline.create(GUI, FULL, pipelineSettings(), generator, NOW)

        then:
        created.id() == null
        created.service() == GUI
        created.type() == FULL
        created.version() == 0
        created.createdAt() == null
        created.updatedAt() == null
        created.enabled
        created.keys() == [PipelineKey.builder().value('key-1').status(ACTIVE).issuedAt(NOW).build()]
        created.activeKey().get().active
        pipeline(id: 20, productId: 3, serviceId: 30, version: 7).with { [id(), service(), version(), createdAt(), updatedAt()] } ==
                [20L, new ServiceRef(3, 30), 7L, Instant.parse('2026-10-01T08:00:00Z'), Instant.parse('2026-10-02T09:30:00Z')]
    }

    def "issuing a key revokes the active one as replaced"() {
        given:
        def pipeline = Pipeline.create(GUI, FULL, pipelineSettings(), generator, NOW)

        when:
        def second = pipeline.issueKey(generator, LATER)

        then:
        second == PipelineKey.builder().value('key-2').status(ACTIVE).issuedAt(LATER).build()
        pipeline.activeKey().get() == second
        pipeline.keys()*.value() == ['key-2', 'key-1']
        pipeline.keys()[1] == new PipelineKey(null, 'key-1', REVOKED, NOW, LATER, REPLACED_REASON, null)
        REPLACED_REASON == 'Replaced by a new key'
    }

    def "revoking the key disables the pipeline and keeps the reason"() {
        given:
        def pipeline = pipeline(keys: [activeKey(), revokedKey()])

        when:
        def revoked = pipeline.revokeActiveKey('  Service retired  ', LATER)

        then:
        !pipeline.enabled
        pipeline.activeKey().isEmpty()
        revoked.status() == REVOKED
        revoked.revokeReason() == 'Service retired'
        revoked.revokedAt() == LATER
        revoked.id() == 100L
        pipeline.keys() == [revoked, revokedKey()]
    }

    def "a disabled pipeline is enabled again with a new key"() {
        given:
        def pipeline = pipeline(keys: [revokedKey(reason: 'paused')])

        when:
        pipeline.issueKey(generator, LATER)

        then:
        pipeline.enabled
        pipeline.keys()*.status() == [ACTIVE, REVOKED]
        pipeline.keys()[1].revokeReason() == 'paused'
        pipeline.keys()[1].revokedAt() == revokedKey().revokedAt()
    }

    def "a key revoked without a reason is refused without one"() {
        given:
        def revoked = revokedKey(reason: null)

        when:
        revoked.requireActive()

        then:
        def e = thrown(SecurityException)
        e.message == "The DevSecOps pipeline key was invalidated on ${revoked.revokedAt()}"
    }

    def "#refusal is refused"() {
        when:
        action()

        then:
        def e = thrown(failure)
        e.message == message

        where:
        refusal                         | action                                                             || failure                       | message
        'revoking without an active key' | { pipeline(keys: [revokedKey()]).revokeActiveKey('again', LATER) } || IllegalStateException         | 'The pipeline has no active key to invalidate'
        'a second active key'           | { pipeline(keys: [activeKey(), activeKey(id: 101, value: 'x')]) }  || IllegalArgumentException      | 'a pipeline has at most one active key'
        'changing the key history'      | { pipeline().keys().clear() }                                      || UnsupportedOperationException | null
        'changing the type'             | { pipeline().reconfigure(null, SAST, pipelineSettings()) }         || IllegalStateException         | 'The type of a pipeline cannot change; add a new pipeline instead'
        'a reason over 500 bytes'       | { pipeline().revokeActiveKey('ł' * 251, LATER) }                   || InvalidRequestException       | 'is too long: it may take at most 500 bytes'
    }

    def "only the security pipeline keeps the extended pipeline job and only the extended one the security job"() {
        when:
        def created = Pipeline.create(GUI, type, pipelineSettings(extendedPipelineJob: 'CERT/gui-extended',
                securityPipelineJob: 'CERT/gui-security', agentLabels: [' linux ', 'linux', 'docker']), generator, NOW)

        then:
        created.settings().extendedPipelineJob() == extended
        created.settings().securityPipelineJob() == security
        created.settings().agentLabels() == ['linux', 'docker']

        where:
        type     || extended            | security
        SECURITY || 'CERT/gui-extended' | null
        EXTENDED || null                | 'CERT/gui-security'
        FULL     || null                | null
        SAST     || null                | null
        NEXUS_IQ || null                | null
    }

    def "reconfiguring changes the settings but not the keys"() {
        given:
        def pipeline = pipeline(type: SECURITY)
        def keys = pipeline.keys()

        when:
        pipeline.reconfigure(0L, SECURITY, new PipelineSettings(['windows'], ' CERT/ext ', 'CERT/sec', ' CERT/gui ',
                ' nightly '))

        then:
        pipeline.settings() == new PipelineSettings(['windows'], 'CERT/ext', null, 'CERT/gui', 'nightly')
        pipeline.keys() == keys
    }

    def "agent labels that do not fit their column are refused when a pipeline is created or reconfigured"() {
        given:
        def labels = (1..20).collect { "agent-$it-${'x' * 50}".toString() }
        def tooMany = new PipelineSettings(labels, null, null, null, null)
        def existing = pipeline()

        when:
        Pipeline.create(GUI, FULL, tooMany, generator, NOW)

        then:
        def created = thrown(InvalidRequestException)
        created.problems()*.field() == ['agentLabels']

        when:
        existing.reconfigure(null, existing.type(), tooMany)

        then:
        def reconfigured = thrown(InvalidRequestException)
        reconfigured.problems()*.message() == ['is too long: all entries together may take at most 1000 bytes']
        existing.settings() != tooMany
    }

    def "settings read at an older version are refused before anything changes, settings without a version are not checked"() {
        given:
        def pipeline = pipeline(version: 3)
        def settings = pipeline.settings()

        when:
        pipeline.reconfigure(2L, FULL, pipelineSettings(jenkinsJob: 'CERT/gui-full'))

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        pipeline.settings() == settings

        when:
        pipeline.reconfigure(null, FULL, pipelineSettings(jenkinsJob: 'CERT/gui-full'))
        pipeline.reconfigure(3L, FULL, pipelineSettings(jenkinsJob: 'CERT/gui'))

        then:
        pipeline.settings().jenkinsJob() == 'CERT/gui'
    }

    def "a Jenkins job and a description that fit their column in characters but not in UTF-8 bytes are refused"() {
        when:
        Pipeline.create(GUI, FULL, pipelineSettings(jenkinsJob: 'Zespół/' * 125, description: 'ś' * 501),
                generator, NOW)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field() == ['jenkinsJob', 'description']
        e.problems()*.message().unique() == ['is too long: it may take at most 1000 bytes']

        when:
        def fitting = Pipeline.create(GUI, FULL, pipelineSettings(jenkinsJob: 'Zespół/' * 111, description: 'ś' * 500),
                generator, NOW)

        then:
        fitting.settings().description().length() == 500
    }

    def "the #type pipeline reads its metrics from project tag #tag"() {
        expect:
        type.influxProjectTag('cert-gui') == tag

        where:
        type     || tag
        FULL     || 'cert-gui'
        SECURITY || 'cert-guisecurity'
        EXTENDED || 'cert-guiextended'
        SAST     || 'cert-guisast'
        NEXUS_IQ || 'cert-guinexusiq'
    }

    def "each type names the library entry point it runs"() {
        expect:
        PipelineType.values().collect { [it.entryPoint(), it.variant()] } == [
                ['devSecOpsPipeline', 'full'], ['devSecOpsSecurityPipeline', 'security'],
                ['devSecOpsExtendedPipeline', 'extended'], ['devSecOpsSASTScanningPipeline', 'sast'],
                ['devSecOpsNexusIqGoldenFixPipeline', 'nexusiq']]
    }
}
