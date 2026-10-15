package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PipelineKeyJpaRepository extends JpaRepository<PipelineKeyEntity, Long> {

    @Query("""
            select new com.bbh.itss.dso.portal.adapter.out.persistence.IssuedKeyRow(k.pipeline.id, k.id, k.value,
                k.status, k.issuedAt, k.revokedAt, k.revokeReason, k.lastUsedAt)
            from PipelineKeyEntity k where k.value = :value""")
    Optional<IssuedKeyRow> findIssuedKey(@Param("value") String value);

    @Modifying
    @Query("""
            update PipelineKeyEntity k set k.lastUsedAt = :usedAt
            where k.id = :id and k.status = com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE""")
    int recordUse(@Param("id") Long id, @Param("usedAt") Instant usedAt);
}
