package com.bbh.itss.dso.portal.application.actionlog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase;
import com.bbh.itss.dso.portal.application.actionlog.port.out.ActionLogRepositoryPort;
import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;

@UseCase
@RequiredArgsConstructor
public class ActionLogService implements ActionLogUseCase {

    private final ActionLogRepositoryPort repository;

    @Override
    @ReadOnly
    public List<ActionLog> listLogs(String search, String actionType, String resourceType, String status) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String cleanActionType = (actionType != null && !actionType.isBlank() && !"ALL".equalsIgnoreCase(actionType)) ? actionType.trim() : null;
        String cleanResourceType = (resourceType != null && !resourceType.isBlank() && !"ALL".equalsIgnoreCase(resourceType)) ? resourceType.trim() : null;
        String cleanStatus = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) ? status.trim() : null;

        if (cleanSearch == null && cleanActionType == null && cleanResourceType == null && cleanStatus == null) {
            return repository.listAll();
        }

        return repository.search(cleanSearch, cleanActionType, cleanResourceType, cleanStatus);
    }

    @Override
    public void log(String username, String userDisplayName, String userEmail, String departmentName,
                    String actionType, String resourceType, String resourceName, String details, String status) {
        ActionLog actionLog = new ActionLog(
                null,
                Instant.now(),
                username != null ? username : "system",
                userDisplayName != null ? userDisplayName : "System",
                userEmail,
                departmentName,
                actionType,
                resourceType,
                resourceName,
                details,
                status != null ? status : "SUCCESS"
        );
        repository.save(actionLog);
    }
}
