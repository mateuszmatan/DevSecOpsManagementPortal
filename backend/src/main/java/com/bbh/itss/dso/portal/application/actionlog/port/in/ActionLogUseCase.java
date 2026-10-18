package com.bbh.itss.dso.portal.application.actionlog.port.in;

import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;

import java.util.List;

public interface ActionLogUseCase {

    /** The newest first. */
    List<ActionLog> list();

    void record(ActionRecord action);
}
