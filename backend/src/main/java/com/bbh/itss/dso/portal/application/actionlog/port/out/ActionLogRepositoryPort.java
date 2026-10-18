package com.bbh.itss.dso.portal.application.actionlog.port.out;

import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;

import java.util.List;

public interface ActionLogRepositoryPort {

    /** The newest first. */
    List<ActionLog> findAll();

    void save(ActionLog log);
}
