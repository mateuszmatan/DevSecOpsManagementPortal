package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private static final String DEPARTMENT = "DEPARTMENT";

    private final DepartmentsUseCase departments;
    private final ActionRecorder actions;

    @GetMapping
    public List<DepartmentResponse> list() {
        return departments.list().stream().map(DepartmentResponse::of).toList();
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse created = actions.record(ActionRecord.of("CREATE_DEPARTMENT", DEPARTMENT, request.name(), null),
                () -> DepartmentResponse.of(departments.create(request.name())),
                (action, department) -> action.withDepartmentId(department.id()).done("Created business service"));
        return created(fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    public DepartmentResponse rename(@PathVariable long id, @Valid @RequestBody DepartmentRequest request) {
        String before = nameOf(id);
        return actions.record(ActionRecord.of("RENAME_DEPARTMENT", DEPARTMENT, before, id),
                () -> DepartmentResponse.of(departments.rename(id, request.version(), request.name())),
                (action, department) -> action.withResourceName(department.name())
                        .done("Renamed business service " + before + " to " + department.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        actions.record(ActionRecord.of("DELETE_DEPARTMENT", DEPARTMENT, nameOf(id), null),
                () -> departments.delete(id), "Deleted business service");
    }

    private String nameOf(long id) {
        return departments.list().stream().filter(department -> department.id() == id).map(DepartmentView::name)
                .findFirst().orElse("#" + id);
    }
}
