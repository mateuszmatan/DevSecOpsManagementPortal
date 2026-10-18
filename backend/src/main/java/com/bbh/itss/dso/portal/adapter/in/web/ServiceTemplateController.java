package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageServiceTemplateUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-template")
@RequiredArgsConstructor
public class ServiceTemplateController {

    private final ManageServiceTemplateUseCase template;
    private final ActionRecorder actions;

    @GetMapping
    public ServiceTemplateDto get() {
        return ServiceTemplateDto.from(template.current());
    }

    @PutMapping
    public ServiceTemplateDto update(@Valid @RequestBody ServiceTemplateDto request) {
        return actions.record(ActionRecord.of("UPDATE_SERVICE_TEMPLATE", "SETTINGS", "Service template", null),
                () -> ServiceTemplateDto.from(template.update(request.version(), request.toTemplate())),
                "Updated the service template");
    }
}
