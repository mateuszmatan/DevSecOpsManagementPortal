package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.actionlog.ActionLog;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_ACTION_LOG")
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
public class ActionLogEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Instant createdAt;

    private String username;
    private String userDisplayName;
    private String userEmail;
    private String departmentName;
    private String actionType;
    private String resourceType;
    private String resourceName;
    private String details;
    private String status;

    static ActionLogEntity of(ActionLog log) {
        return new ActionLogEntity(null, null, log.username(), log.userDisplayName(), log.userEmail(),
                log.departmentName(), log.actionType(), log.resourceType(), log.resourceName(), log.details(),
                log.status());
    }

    @PrePersist
    void onCreate() {
        createdAt = Timestamps.now();
    }

    ActionLog toDomain() {
        return map(ActionLog.class, this);
    }
}
