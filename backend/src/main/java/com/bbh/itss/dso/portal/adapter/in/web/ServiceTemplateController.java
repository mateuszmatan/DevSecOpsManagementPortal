package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.settings.port.in.ManageServiceTemplateUseCase;
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
@RequestMapping("/api/service-template")
@RequiredArgsConstructor
public class ServiceTemplateController {

    private final ManageServiceTemplateUseCase template;

    @GetMapping
    public ServiceTemplateDto get() {
        log.info("Fetching default service template");
        return ServiceTemplateDto.from(template.current());
    }

    @PutMapping
    public ServiceTemplateDto update(@Valid @RequestBody ServiceTemplateDto request) {
        log.info("Updating service template (version={})", request.version());
        ServiceTemplateDto updated = ServiceTemplateDto.from(template.update(request.version(), request.toTemplate()));
        log.info("Successfully updated service template");
        return updated;
    }
}
