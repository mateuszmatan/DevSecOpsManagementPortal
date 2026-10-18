package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.actionlog.port.out.ActionLogRepositoryPort;
import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
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
    public void save(ActionLog logItem) {
        log.debug("Persisting action log: {} on {}", logItem.actionType(), logItem.resourceName());
        ActionLogEntity entity = ActionLogEntity.builder()
                .username(logItem.username())
                .userDisplayName(logItem.userDisplayName())
                .userEmail(logItem.userEmail())
                .businessServiceName(logItem.businessServiceName() != null ? logItem.businessServiceName() : logItem.departmentName())
                .departmentName(logItem.departmentName())
                .actionType(logItem.actionType())
                .resourceType(logItem.resourceType())
                .resourceName(logItem.resourceName())
                .details(logItem.details())
                .status(logItem.status())
                .build();
        repository.save(entity);
    }
}
