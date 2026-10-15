package com.bbh.itss.dso.portal.application.catalog.port.out;

import com.bbh.itss.dso.portal.domain.shared.Text;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ProductSummary(long id, String code, String name, String description, String ownerTeam,
                             Long departmentId, String departmentName, Instant updatedAt) {

    public boolean matches(String search) {
        return Text.matches(search, name, code, ownerTeam, departmentName, description);
    }
}
