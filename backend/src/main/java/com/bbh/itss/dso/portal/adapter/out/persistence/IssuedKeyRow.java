package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.pipeline.IssuedKey;
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;

import java.time.Instant;

record IssuedKeyRow(Long pipelineId, Long id, String value, KeyStatus status, Instant issuedAt, Instant revokedAt,
                    String revokeReason, Instant lastUsedAt) {

    IssuedKey toDomain() {
        return new IssuedKey(pipelineId, RecordMapper.map(PipelineKey.class, this));
    }
}
