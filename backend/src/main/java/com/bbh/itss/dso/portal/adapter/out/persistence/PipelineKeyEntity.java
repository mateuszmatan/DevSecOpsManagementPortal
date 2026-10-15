package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_PIPELINE_KEY")
@NoArgsConstructor(access = PROTECTED)
public class PipelineKeyEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Getter(PACKAGE)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "PIPELINE_ID")
    private PipelineEntity pipeline;

    @Column(name = "KEY_VALUE", updatable = false)
    private String value;

    @Enumerated(STRING)
    private KeyStatus status;

    @Column(updatable = false)
    private Instant issuedAt;

    private Instant revokedAt;
    private String revokeReason;
    private Instant lastUsedAt;

    PipelineKeyEntity(PipelineEntity pipeline, String value, Instant issuedAt) {
        this.pipeline = pipeline;
        this.value = value;
        this.issuedAt = issuedAt;
    }

    void state(KeyStatus status, Instant revokedAt, String revokeReason) {
        this.status = status;
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
    }

    PipelineKey toDomain() {
        return RecordMapper.map(PipelineKey.class, this);
    }
}
