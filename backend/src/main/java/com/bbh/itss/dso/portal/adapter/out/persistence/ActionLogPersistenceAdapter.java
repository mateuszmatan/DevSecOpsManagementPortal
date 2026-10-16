package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.actionlog.port.out.ActionLogRepositoryPort;
import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class ActionLogPersistenceAdapter implements ActionLogRepositoryPort {

    private final ActionLogJpaRepository repository;

    @Override
    public List<ActionLog> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(ActionLogEntity::toDomain)
                .toList();
    }

    @Override
    public List<ActionLog> search(String search, String actionType, String resourceType, String status) {
        return repository.searchLogs(search, actionType, resourceType, status).stream()
                .map(ActionLogEntity::toDomain)
                .toList();
    }

    @Override
    public void save(ActionLog log) {
        ActionLogEntity entity = new ActionLogEntity(
                log.username(),
                log.userDisplayName(),
                log.userEmail(),
                log.departmentName(),
                log.actionType(),
                log.resourceType(),
                log.resourceName(),
                log.details(),
                log.status()
        );
        repository.save(entity);
    }
}
