package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Component
@Order(HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalSettingsInitializer implements ApplicationRunner {

    private final ManageGlobalSettingsUseCase settings;

    @Override
    public void run(ApplicationArguments args) {
        settings.ensureExists();
    }
}
