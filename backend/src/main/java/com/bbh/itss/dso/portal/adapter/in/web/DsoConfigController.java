package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.RenderConfigUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.in.web.ApiExceptionHandler.problem;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.ResponseEntity.ok;
import static org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK;

@RestController
@RequiredArgsConstructor
public class DsoConfigController {

    private static final MediaType YAML = new MediaType("application", "yaml");
    private static final DumperOptions YAML_OPTIONS = yamlOptions();

    private final RenderConfigUseCase configs;

    @GetMapping("/api/dso/config/{key}")
    public ResponseEntity<?> pipelineConfig(@PathVariable String key,
                                            @RequestParam(defaultValue = "yaml") String format) {
        return render(configs.readByKey(key), format);
    }

    @GetMapping("/api/pipelines/{id}/config")
    public ResponseEntity<?> pipelineConfigPreview(@PathVariable long id,
                                                   @RequestParam(defaultValue = "yaml") String format) {
        return render(configs.pipelineConfig(id), format);
    }

    @GetMapping("/api/products/{id}/config")
    public ResponseEntity<?> productConfig(@PathVariable long id, @RequestParam(defaultValue = "yaml") String format) {
        return render(configs.productConfig(id), format);
    }

    @GetMapping("/api/settings/config")
    public ResponseEntity<?> globalConfig(@RequestParam(defaultValue = "yaml") String format) {
        return render(configs.settingsConfig(), format);
    }

    @ExceptionHandler(SecurityException.class)
    ProblemDetail revoked(SecurityException e) {
        return problem(FORBIDDEN, "Pipeline key invalidated", e.getMessage());
    }

    private ResponseEntity<?> render(Map<String, Object> config, String format) {
        if ("json".equalsIgnoreCase(format)) {
            return ok().contentType(APPLICATION_JSON).body(config);
        }
        return ok().contentType(YAML).body(new Yaml(new Representer(YAML_OPTIONS), YAML_OPTIONS).dump(config));
    }

    private static DumperOptions yamlOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setPrettyFlow(true);
        options.setWidth(160);
        return options;
    }
}
