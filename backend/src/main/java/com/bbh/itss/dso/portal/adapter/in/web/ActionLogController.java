package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase;
import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/action-history")
@RequiredArgsConstructor
public class ActionLogController {

    private final ActionLogUseCase actionLog;

    @GetMapping
    public List<ActionLog> list() {
        return actionLog.list();
    }
}
