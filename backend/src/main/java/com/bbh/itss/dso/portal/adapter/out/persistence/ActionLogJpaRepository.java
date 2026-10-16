package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActionLogJpaRepository extends JpaRepository<ActionLogEntity, Long> {

    List<ActionLogEntity> findAllByOrderByCreatedAtDesc();

    @Query("""
        SELECT a FROM ActionLogEntity a
        WHERE (:search IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(a.userDisplayName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(a.resourceName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(a.details) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:actionType IS NULL OR a.actionType = :actionType)
          AND (:resourceType IS NULL OR a.resourceType = :resourceType)
          AND (:status IS NULL OR a.status = :status)
        ORDER BY a.createdAt DESC
    """)
    List<ActionLogEntity> searchLogs(
            @Param("search") String search,
            @Param("actionType") String actionType,
            @Param("resourceType") String resourceType,
            @Param("status") String status
    );
}
