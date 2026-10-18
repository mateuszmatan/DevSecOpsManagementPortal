package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequestUri;

@Slf4j
@RestController
@RequestMapping({"/api/departments", "/api/business-services"})
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentsUseCase departments;
    private final ActionLogUseCase actionLog;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<DepartmentResponse> list() {
        log.info("Listing all departments");
        return departments.list().stream().map(DepartmentResponse::of).toList();
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        log.info("Creating department with name='{}'", request.name());
        DepartmentResponse created = DepartmentResponse.of(departments.create(request.name()));
        log.info("Successfully created department id={}, name='{}'", created.id(), created.name());
        recordLog("CREATE_DEPARTMENT", created.name(), "Created department " + created.name());
        return created(fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    public DepartmentResponse rename(@PathVariable long id, @Valid @RequestBody DepartmentRequest request) {
        log.info("Renaming department id={} to name='{}'", id, request.name());
        DepartmentResponse response = DepartmentResponse.of(departments.rename(id, request.version(), request.name()));
        log.info("Successfully renamed department id={} to name='{}'", id, response.name());
        recordLog("RENAME_DEPARTMENT", response.name(), "Renamed department #" + id + " to " + response.name());
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("Deleting department id={}", id);
        departments.delete(id);
        log.info("Successfully deleted department id={}", id);
        recordLog("DELETE_DEPARTMENT", "Department #" + id, "Deleted department #" + id);
    }

    private void recordLog(String actionType, String resourceName, String details) {
        try {
            com.bbh.itss.dso.portal.domain.user.UserProfile user = currentUserProvider.getCurrentUser();
            actionLog.log(
                    user != null ? user.username() : "system",
                    user != null ? user.displayName() : "System",
                    user != null ? user.email() : null,
                    user != null && !user.departmentNames().isEmpty() ? user.departmentNames().get(0) : null,
                    actionType,
                    "DEPARTMENT",
                    resourceName,
                    details,
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.warn("Failed to record action log for actionType={}, resourceName={}: {}", actionType, resourceName, e.getMessage());
        }
    }
}
