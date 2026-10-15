package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service

class ServiceTemplateRegressionSpec extends PortalSpecification {

    Map original

    def setup() {
        original = api.get('/api/service-template').json as Map
    }

    def cleanup() {
        def current = api.get('/api/service-template').json
        if (current.version != original.version) {
            def restored = api.put('/api/service-template', original + [version: current.version])
            assert restored.status == 200: restored
        }
    }

    def "the template starts as the BBH defaults the self-service wizard fills in"() {
        expect:
        with(original) {
            agentLabels == ['linux-agent']
            jenkinsJob == 'DevSecOps/{CODE}/{service}-{type}'
            gradleTasks == 'clean build'
            gradleArtifact == 'build/libs/*.jar'
            mavenTasks == 'clean verify'
            mavenArtifact == 'target/*.jar'
            nexusIqApplication == '{code}-{service}'
            repositoryUrl == 'https://bitbucket.bbh.com/projects/{CODE}/repos/{code}-{service}'
            bitbucketCredentialsId == 'bitbucket-http-credentials'
            openShiftProject == '{code}-{service}'
            imageRegistry == 'docker-qc.tools.bbh.com'
            healthCheckUrl == '/actuator/health'
        }
    }

    def "a change is stored as a new version and names the pipelines of every new service"() {
        when:
        def changed = api.put('/api/service-template', original + [agentLabels: ['linux', 'docker'],
                                                                   jenkinsJob : 'Teams/{CODE}/{service}/{type}'])
        def code = uniqueCode('TPL')
        def created = createProduct(product(code: code, name: "Template $code", services: [service(name: 'gui')]))
        def full = pipelineOfService(created.services[0].id as long)

        then:
        changed.status == 200
        changed.json.version == (original.version == null ? 0 : original.version + 1)
        changed.json.updatedAt != null
        changed.json.jenkinsJob == 'Teams/{CODE}/{service}/{type}'
        api.get('/api/service-template').json == changed.json
        full.agentLabels == ['linux', 'docker']
        full.jenkinsJob == "Teams/$code/gui/full"
    }

    def "a change based on an outdated version is refused"() {
        given:
        def first = api.put('/api/service-template', original + [gradleTasks: 'build'])

        when:
        def stale = api.put('/api/service-template', original + [gradleTasks: 'assemble'])

        then:
        first.status == 200
        stale.status == 409
        api.get('/api/service-template').json.gradleTasks == 'build'
    }

    def "unknown placeholders and patterns too long once filled in are refused with every problem"() {
        when:
        def response = api.put('/api/service-template', original + [
                jenkinsJob      : 'Jobs/{product}/{service}',
                openShiftProject: '{code}-{service}-' + 'x' * 50])

        then:
        response.status == 400
        response.json.errors.collect { [it.field, it.message] } == [
                ['jenkinsJob', 'knows no placeholder {product}: use {CODE}, {code}, {service}, {type}'],
                ['openShiftProject', 'is too long once the longest product code and service name are filled in: it may take at most 194 bytes']]
        api.get('/api/service-template').json.version == original.version
    }

    def "malformed templates are refused before they reach the business rules"() {
        expect:
        api.put('/api/service-template', change(original)).status == 400

        where:
        change << [{ Map it -> it + [agentLabels: []] },
                   { Map it -> it + [agentLabels: ['linux,docker']] },
                   { Map it -> it + [jenkinsJob: 'DevSecOps/../{service}'] },
                   { Map it -> it + [gradleArtifact: 'build/libs/*.jar; rm -rf /'] },
                   { Map it -> it + [repositoryUrl: 'bitbucket.bbh.com/{code}'] },
                   { Map it -> it + [openShiftProject: '{code}_{service}'] },
                   { Map it -> it + [imageRegistry: 'docker qc'] }]
    }
}
