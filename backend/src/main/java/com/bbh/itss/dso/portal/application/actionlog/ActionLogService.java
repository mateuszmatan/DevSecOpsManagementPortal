package com.bbh.itss.dso.portal.application.actionlog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase;
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.dso.portal.application.actionlog.port.out.ActionLogRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import com.bbh.itss.dso.portal.domain.user.UserProfile;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.trim;

@UseCase
@RequiredArgsConstructor
public class ActionLogService implements ActionLogUseCase {

    private static final int NAME_MAX = 200;
    private static final int DETAILS_MAX = 4000;

    private final ActionLogRepositoryPort repository;
    private final DepartmentRepositoryPort departments;

    @Override
    @ReadOnly
    public List<ActionLog> list() {
        return repository.findAll();
    }

    @Override
    public void record(ActionRecord action) {
        UserProfile user = action.user();
        String department = action.departmentId() == null ? null
                : departments.load(action.departmentId()).map(Department::name).orElse(null);
        repository.save(new ActionLog(null, null, user.username(), user.displayName(), user.email(), department,
                action.actionType(), action.resourceType(), abbreviate(trim(action.resourceName()), NAME_MAX),
                abbreviate(action.details(), DETAILS_MAX), action.success() ? "SUCCESS" : "FAILURE"));
    }
}
