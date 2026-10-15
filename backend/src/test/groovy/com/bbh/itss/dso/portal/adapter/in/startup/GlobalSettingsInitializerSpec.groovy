package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import org.springframework.boot.ApplicationArguments
import org.springframework.core.annotation.Order
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.settings.GlobalSettings.bbhDefaults
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE

class GlobalSettingsInitializerSpec extends Specification {

    ManageGlobalSettingsUseCase settings = Mock()

    def "the settings exist before anything else runs at start-up"() {
        given:
        def initializer = new GlobalSettingsInitializer(settings)

        when:
        initializer.run(Stub(ApplicationArguments))

        then:
        1 * settings.ensureExists() >> bbhDefaults()
        GlobalSettingsInitializer.getAnnotation(Order).value() == HIGHEST_PRECEDENCE
    }
}
