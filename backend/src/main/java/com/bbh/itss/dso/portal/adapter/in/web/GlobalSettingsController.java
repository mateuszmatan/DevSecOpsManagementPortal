package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class GlobalSettingsController {

    private final ManageGlobalSettingsUseCase settings;
    private final ActionRecorder actions;

    @GetMapping
    public GlobalSettingsDto get() {
        return GlobalSettingsDto.from(settings.current());
    }

    @PutMapping
    public GlobalSettingsDto update(@Valid @RequestBody GlobalSettingsDto request) {
        return actions.record(ActionRecord.of("UPDATE_GLOBAL_SETTINGS", "SETTINGS", "Library defaults", null),
                () -> GlobalSettingsDto.from(settings.update(request.version(), request.toValues())),
                "Updated the library defaults");
    }
}
