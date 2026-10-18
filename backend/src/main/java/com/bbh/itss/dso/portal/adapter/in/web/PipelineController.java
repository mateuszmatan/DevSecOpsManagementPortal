package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.in.web.MonitoringController.PipelineHealthResponse;
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.dso.portal.application.monitoring.port.in.DepartmentPipelines;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

import static com.bbh.itss.dso.portal.adapter.in.web.PipelineResponse.withKeys;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PipelineController {

    private static final String PIPELINE = "PIPELINE";
    private static final String PIPELINE_KEY = "PIPELINE_KEY";

    private final PipelinesUseCase pipelines;
    private final MonitorPipelinesUseCase monitoring;
    private final ActionRecorder actions;

    @GetMapping("/pipelines")
    public DepartmentPipelinesResponse listForDepartment(@RequestParam long departmentId) {
        DepartmentPipelines found = monitoring.department(departmentId);
        return new DepartmentPipelinesResponse(found.pipelines().stream().map(PipelineHealthResponse::of).toList(),
                found.metricsError());
    }

    @GetMapping("/products/{productId}/pipelines")
    public List<ServicePipelinesResponse> listForProduct(@PathVariable long productId) {
        return pipelines.listForProduct(productId).stream().map(ServicePipelinesResponse::of).toList();
    }

    @PostMapping("/services/{serviceId}/pipelines")
    @ResponseStatus(CREATED)
    public PipelineResponse create(@PathVariable long serviceId, @Valid @RequestBody PipelineRequest request) {
        return withKeys(actions.record(ActionRecord.of("CREATE_PIPELINE", PIPELINE, "service #" + serviceId, null),
                () -> pipelines.create(serviceId, request.type(), request.toSettings()),
                (action, view) -> described(action, view).done("Created pipeline")));
    }

    @GetMapping("/pipelines/{id}")
    public PipelineResponse get(@PathVariable long id) {
        return withKeys(pipelines.get(id));
    }

    @PutMapping("/pipelines/{id}")
    public PipelineResponse update(@PathVariable long id, @Valid @RequestBody PipelineRequest request) {
        return record("UPDATE_PIPELINE", PIPELINE, id,
                () -> pipelines.update(id, request.version(), request.type(), request.toSettings()), "Updated pipeline");
    }

    @DeleteMapping("/pipelines/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        actions.record(described(ActionRecord.of("DELETE_PIPELINE", PIPELINE, null, null), pipelines.get(id)),
                () -> pipelines.delete(id), "Deleted pipeline");
    }

    @PostMapping("/pipelines/{id}/keys/revoke")
    public PipelineResponse revokeKey(@PathVariable long id, @Valid @RequestBody RevokeKeyRequest request) {
        return record("REVOKE_KEY", PIPELINE_KEY, id, () -> pipelines.revokeKey(id, request.reason()),
                "Invalidated the active key: " + request.reason());
    }

    @PostMapping("/pipelines/{id}/keys")
    public PipelineResponse issueKey(@PathVariable long id) {
        return record("ROTATE_KEY", PIPELINE_KEY, id, () -> pipelines.issueKey(id), "Issued a new key");
    }

    private PipelineResponse record(String actionType, String resourceType, long id, Supplier<PipelineView> change,
                                    String details) {
        return withKeys(actions.record(described(ActionRecord.of(actionType, resourceType, null, null), pipelines.get(id)),
                change, (action, view) -> described(action, view).done(details)));
    }

    private static ActionRecord described(ActionRecord action, PipelineView view) {
        return action.withResourceName(view.service().name() + " · " + view.pipeline().type().label())
                .withDepartmentId(view.product().departmentId());
    }

    public record DepartmentPipelinesResponse(List<PipelineHealthResponse> pipelines, String metricsError) {
    }

    public record ServicePipelinesResponse(Long serviceId, String serviceName, String description, BuildTool buildTool,
                                           DeployTarget deployTarget, List<PipelineResponse> pipelines) {

        static ServicePipelinesResponse of(ServicePipelinesView view) {
            Service service = view.service();
            return new ServicePipelinesResponse(service.id(), service.name(), service.description(),
                    service.settings().build().tool(), service.settings().deployment().target(),
                    view.pipelines().stream().map(PipelineResponse::summary).toList());
        }
    }
}
