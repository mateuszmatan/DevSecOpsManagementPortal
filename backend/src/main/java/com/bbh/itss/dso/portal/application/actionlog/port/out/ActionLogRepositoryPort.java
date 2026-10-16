package com.bbh.itss.dso.portal.application.actionlog.port.out;

import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;

import java.util.List;

public interface ActionLogRepositoryPort {

    List<ActionLog> listAll();

    List<ActionLog> search(String search, String actionType, String resourceType, String status);

    void save(ActionLog log);
}
