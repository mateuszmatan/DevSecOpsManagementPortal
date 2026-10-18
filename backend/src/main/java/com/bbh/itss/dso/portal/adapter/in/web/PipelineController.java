package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.in.web.MonitoringController.PipelineHealthResponse;
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.in.DepartmentPipelines;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.user.UserProfile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import static com.bbh.itss.dso.portal.adapter.in.web.PipelineResponse.withKeys;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelinesUseCase pipelines;
    private final MonitorPipelinesUseCase monitoring;
    private final ActionLogUseCase actionLog;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping(value = "/pipelines", params = "businessServiceId")
    public DepartmentPipelinesResponse listForBusinessService(@RequestParam long businessServiceId) {
        log.info("Listing pipelines for businessServiceId={}", businessServiceId);
        DepartmentPipelines found = monitoring.department(businessServiceId);
        return new DepartmentPipelinesResponse(found.pipelines().stream().map(PipelineHealthResponse::of).toList(),
                found.metricsError());
    }

    @GetMapping(value = "/pipelines", params = "departmentId")
    public DepartmentPipelinesResponse listForDepartment(@RequestParam long departmentId) {
        log.info("Listing pipelines for departmentId={}", departmentId);
        DepartmentPipelines found = monitoring.department(departmentId);
        return new DepartmentPipelinesResponse(found.pipelines().stream().map(PipelineHealthResponse::of).toList(),
                found.metricsError());
    }

    @GetMapping("/products/{productId}/pipelines")
    public List<ServicePipelinesResponse> listForProduct(@PathVariable long productId) {
        log.info("Listing pipelines for productId={}", productId);
        return pipelines.listForProduct(productId).stream().map(ServicePipelinesResponse::of).toList();
    }

    @PostMapping("/services/{serviceId}/pipelines")
    @ResponseStatus(CREATED)
    public PipelineResponse create(@PathVariable long serviceId, @Valid @RequestBody PipelineRequest request) {
        log.info("Creating pipeline for serviceId={}, type={}", serviceId, request.type());
        PipelineResponse response = withKeys(pipelines.create(serviceId, request.type(), request.toSettings()));
        log.info("Successfully created pipeline id={} for service='{}' ({})", response.id(), response.serviceName(), response.type());
        recordLog("CREATE_PIPELINE", "PIPELINE", response.serviceName() + " (" + response.type() + ")", "Created pipeline for service " + response.serviceName());
        return response;
    }

    @GetMapping("/pipelines/{id}")
    public PipelineResponse get(@PathVariable long id) {
        log.info("Fetching pipeline details for id={}", id);
        return withKeys(pipelines.get(id));
    }

    @PutMapping("/pipelines/{id}")
    public PipelineResponse update(@PathVariable long id, @Valid @RequestBody PipelineRequest request) {
        log.info("Updating pipeline id={}, type={}", id, request.type());
        PipelineResponse response = withKeys(pipelines.update(id, request.version(), request.type(), request.toSettings()));
        log.info("Successfully updated pipeline id={}", id);
        recordLog("UPDATE_PIPELINE", "PIPELINE", response.serviceName() + " (" + response.type() + ")", "Updated pipeline " + id);
        return response;
    }

    @DeleteMapping("/pipelines/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("Deleting pipeline id={}", id);
        pipelines.delete(id);
        log.info("Successfully deleted pipeline id={}", id);
        recordLog("DELETE_PIPELINE", "PIPELINE", "Pipeline #" + id, "Deleted pipeline #" + id);
    }

    @PostMapping("/pipelines/{id}/keys/revoke")
    public PipelineResponse revokeKey(@PathVariable long id, @Valid @RequestBody RevokeKeyRequest request) {
        log.info("Revoking active key for pipeline id={}, reason='{}'", id, request.reason());
        PipelineResponse response = withKeys(pipelines.revokeKey(id, request.reason()));
        log.info("Successfully revoked key for pipeline id={}", id);
        recordLog("REVOKE_KEY", "PIPELINE_KEY", response.serviceName() + " (" + response.type() + ")", "Invalidated active key. Reason: " + request.reason());
        return response;
    }

    @PostMapping("/pipelines/{id}/keys")
    public PipelineResponse issueKey(@PathVariable long id) {
        log.info("Rotating/issuing key for pipeline id={}", id);
        PipelineResponse response = withKeys(pipelines.issueKey(id));
        log.info("Successfully issued new key for pipeline id={}", id);
        recordLog("ROTATE_KEY", "PIPELINE_KEY", response.serviceName() + " (" + response.type() + ")", "Rotated / generated new pipeline key");
        return response;
    }

    private void recordLog(String actionType, String resourceType, String resourceName, String details) {
        try {
            UserProfile user = currentUserProvider.getCurrentUser();
            actionLog.log(
                    user != null ? user.username() : "system",
                    user != null ? user.displayName() : "System",
                    user != null ? user.email() : null,
                    user != null && !user.departmentNames().isEmpty() ? user.departmentNames().get(0) : null,
                    actionType,
                    resourceType,
                    resourceName,
                    details,
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.warn("Failed to record action log for actionType={}, resourceName={}: {}", actionType, resourceName, e.getMessage());
        }
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
