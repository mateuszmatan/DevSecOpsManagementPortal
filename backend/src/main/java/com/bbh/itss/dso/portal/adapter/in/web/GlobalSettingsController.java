package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class GlobalSettingsController {

    private final ManageGlobalSettingsUseCase settings;

    @GetMapping
    public GlobalSettingsDto get() {
        log.info("Fetching global settings");
        return GlobalSettingsDto.from(settings.current());
    }

    @PutMapping
    public GlobalSettingsDto update(@Valid @RequestBody GlobalSettingsDto request) {
        log.info("Updating global settings (version={})", request.version());
        GlobalSettingsDto updated = GlobalSettingsDto.from(settings.update(request.version(), request.toValues()));
        log.info("Successfully updated global settings");
        return updated;
    }
}
