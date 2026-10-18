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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_ACTION_LOG")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = PROTECTED)
public class ActionLogEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 200)
    private String userDisplayName;

    @Column(length = 200)
    private String userEmail;

    @Column(length = 200)
    private String departmentName;

    @Column(nullable = false, length = 100)
    private String actionType;

    @Column(nullable = false, length = 50)
    private String resourceType;

    @Column(length = 200)
    private String resourceName;

    @Column(length = 4000)
    private String details;

    @Column(nullable = false, length = 50)
    private String status;

    public ActionLogEntity(String username, String userDisplayName, String userEmail,
                           String departmentName, String actionType, String resourceType,
                           String resourceName, String details, String status) {
        this(null, null, username, userDisplayName, userEmail, departmentName, actionType, resourceType, resourceName, details, status);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Timestamps.now();
        }
    }

    public ActionLog toDomain() {
        return new ActionLog(id, createdAt, username, userDisplayName, userEmail,
                departmentName, actionType, resourceType, resourceName, details, status);
    }
}
