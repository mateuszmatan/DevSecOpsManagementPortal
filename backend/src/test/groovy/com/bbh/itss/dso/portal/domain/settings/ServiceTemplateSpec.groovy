package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.ServiceTemplate.bbhDefaults
import static com.bbh.itss.dso.portal.domain.settings.ServiceTemplate.fill
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION

class ServiceTemplateSpec extends Specification {

    def bbh = bbhDefaults()

    def "the BBH defaults name every pipeline after its product, service and type and pass their own checks"() {
        expect:
        with(bbh) {
            agentLabels() == ['linux-agent']
            jenkinsJob() == 'DevSecOps/{CODE}/{service}-{type}'
            gradleTasks() == 'clean build'
            gradleArtifact() == 'build/libs/*.jar'
            mavenTasks() == 'clean verify'
            mavenArtifact() == 'target/*.jar'
            nexusIqApplication() == '{code}-{service}'
            repositoryUrl() == 'https://bitbucket.bbh.com/projects/{CODE}/repos/{code}-{service}'
            openShiftProject() == '{code}-{service}'
            healthCheckUrl() == '/actuator/health'
        }
        problems(bbh) == []
    }

    def "blank values are kept as null and agent labels are cleaned"() {
        expect:
        ServiceTemplate.builder().agentLabels([' linux ', '', 'linux', null, 'docker']).jenkinsJob('  ')
                .gradleTasks(' build ').healthCheckUrl('\t').build() ==
                ServiceTemplate.builder().agentLabels(['linux', 'docker']).gradleTasks('build').build()
        ServiceTemplate.builder().build().agentLabels() == []
    }

    def "the pattern #pattern is filled as #filled"() {
        expect:
        fill(pattern, 'CERT', 'backend-api', 'nexusiq') == filled

        where:
        pattern                                  || filled
        null                                     || null
        'plain'                                  || 'plain'
        'DevSecOps/{CODE}/{service}-{type}'      || 'DevSecOps/CERT/backend-api-nexusiq'
        '{code}-{service}-{code}'                || 'cert-backend-api-cert'
        '{other}/{CODE}/{}'                      || '{other}/CERT/{}'
        'cost $1 {CODE}\\'                       || 'cost $1 CERT\\'
    }

    def "a new pipeline gets the template's agents and its job filled in"() {
        expect:
        bbh.pipelineSettings('CERT', 'gui', NEXUS_IQ) ==
                new PipelineSettings(['linux-agent'], null, null, 'DevSecOps/CERT/gui-nexusiq', null)
        bbh.toBuilder().jenkinsJob(null).build().pipelineSettings('CERT', 'gui', FULL) ==
                new PipelineSettings(['linux-agent'], null, null, null, null)
    }

    def "the template is refused with #problem"() {
        expect:
        problems(change.call(bbh.toBuilder()).build()) == problem

        where:
        change << [
                { it.agentLabels([' ']) },
                { it.agentLabels(['x' * 600, 'y' * 600]) },
                { it.jenkinsJob('Jobs/{product}/{service}/{env}') },
                { it.nexusIqApplication('{code}-{service}-{type}') },
                { it.repositoryUrl('https://bitbucket/{CODE}/' + 'x' * 840 + '/{service}') },
                { it.openShiftProject('{code}-{service}-' + 'x' * 42) },
                { it.openShiftProject('{code}-{service}-' + 'x' * 43) },
                { it.jenkinsJob('{service}' * 9 + '{CODE}' + '{type}' * 6) },
                { it.jenkinsJob('{service}' * 9 + '{CODE}' + '{type}' * 7) },
                { it.jenkinsJob(null).nexusIqApplication(null).repositoryUrl(null).openShiftProject(null) }]
        problem << [
                [['agentLabels', 'add at least one Jenkins agent label']],
                [['agentLabels', 'is too long: all entries together may take at most 1000 bytes']],
                [['jenkinsJob', 'knows no placeholder {product}, {env}: use {CODE}, {code}, {service}, {type}']],
                [['nexusIqApplication', 'knows no placeholder {type}: use {CODE}, {code}, {service}']],
                [['repositoryUrl', 'is too long once the longest product code and service name are filled in: it may take at most 1000 bytes']],
                [],
                [['openShiftProject', 'is too long once the longest product code and service name are filled in: it may take at most 194 bytes']],
                [],
                [['jenkinsJob', 'is too long once the longest product code and service name are filled in: it may take at most 1000 bytes']],
                []]
    }

    def "a change is accepted only at the stored version and only when the template passes its checks"() {
        given:
        def stored = new StoredServiceTemplate(bbh, 4, Instant.parse('2026-10-08T12:00:00Z'))
        def changed = bbh.toBuilder().gradleTasks('build -x test').build()

        expect:
        StoredServiceTemplate.unsaved() == new StoredServiceTemplate(bbh, null, null)
        stored.change(4, changed) == new StoredServiceTemplate(changed, 4, stored.updatedAt())
        StoredServiceTemplate.unsaved().change(null, changed).template() == changed

        when:
        stored.change(3, changed)

        then:
        def stale = thrown(IllegalStateException)
        stale.message == STALE_VERSION

        when:
        stored.change(4, changed.toBuilder().agentLabels([]).build())

        then:
        def invalid = thrown(InvalidRequestException)
        invalid.problems*.field == ['agentLabels']
    }

    private static List<List<String>> problems(ServiceTemplate template) {
        def problems = new ValidationProblems()
        template.validate(problems)
        problems.list().collect { [it.field(), it.message()] }
    }
}
