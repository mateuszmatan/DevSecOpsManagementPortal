package com.bbh.itss.dso.portal.domain.actionlog;

import java.time.Instant;

public record ActionLog(
        Long id,
        Instant createdAt,
        String username,
        String userDisplayName,
        String userEmail,
        String departmentName,
        String actionType,
        String resourceType,
        String resourceName,
        String details,
        String status
) {}
