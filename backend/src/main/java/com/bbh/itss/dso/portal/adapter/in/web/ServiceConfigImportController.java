package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequiredArgsConstructor
public class ServiceConfigImportController {

    private final ManageGlobalSettingsUseCase settings;
    private final JsonMapper json;

    /** Reads a config file into new service forms. It stores nothing, so the action history does not record it. */
    @PostMapping("/api/service-configs/import")
    public ConfigImportResponse importConfig(@Valid @RequestBody ConfigImportRequest request) {
        return new ServiceConfigReader(settings.current().values(), json)
                .read(ConfigFileParser.parse(request.content(), json), request.serviceName());
    }
}
