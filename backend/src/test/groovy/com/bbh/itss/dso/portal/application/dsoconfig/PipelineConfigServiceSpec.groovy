package com.bbh.itss.dso.portal.application.dsoconfig

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class PipelineConfigServiceSpec extends Specification {

    static final String KEY = '6f1c2d3e-0000-4abc-9def-123456789abc'

    PipelinesUseCase pipelines = Mock()
    ProductsUseCase products = Mock()
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }
    def service = new PipelineConfigService(pipelines, products, settings)

    Product certScanner = product(id: 1L, code: 'CERT', services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])

    def "a pipeline key renders the configuration of its pipeline from the current settings"() {
        when:
        def config = service.readByKey(KEY)

        then:
        1 * pipelines.authorizeKey(KEY) >> 100L
        1 * pipelines.get(100L) >> view(FULL)
        config.keySet() as List == ['pipeline', 'platform', 'defaults', 'projects']
        config.pipeline.product == 'CERT'
        config.pipeline.projectNames == 'gui'
        config.platform.jenkinsUrl == 'https://jenkins.test'
        config.projects.keySet() as List == ['gui']
    }

    def "an invalidated or unknown key reads no configuration"() {
        given:
        pipelines.authorizeKey(KEY) >> { throw failure }

        when:
        service.readByKey(KEY)

        then:
        thrown(failure.class)
        0 * pipelines.get(_)

        where:
        failure << [new SecurityException('The DevSecOps pipeline key was invalidated'),
                    new NoSuchElementException('Unknown DevSecOps pipeline key')]
    }

    def "the portal previews a pipeline's, a product's and the settings' configuration"() {
        when:
        def pipeline = service.pipelineConfig(100L)
        def product = service.productConfig(1L)

        then:
        1 * pipelines.get(100L) >> view(SECURITY)
        1 * products.get(1L) >> certScanner
        0 * pipelines.authorizeKey(_)
        pipeline.pipeline.type == 'security'
        product.projects.keySet() as List == ['gui', 'backend-api']
        service.settingsConfig().keySet() as List == ['platform', 'defaults']
    }

    private PipelineView view(PipelineType type) {
        PipelineView.of(certScanner, pipeline(productId: 1L, serviceId: 10L, type: type), 'https://jenkins.test')
    }
}
