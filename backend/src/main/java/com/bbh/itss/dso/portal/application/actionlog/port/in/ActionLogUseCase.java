package com.bbh.itss.dso.portal.application.actionlog.port.in;

import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;

import java.util.List;

public interface ActionLogUseCase {

    List<ActionLog> listLogs(String search, String actionType, String resourceType, String status);

    void log(String username, String userDisplayName, String userEmail, String departmentName,
             String actionType, String resourceType, String resourceName, String details, String status);
}
