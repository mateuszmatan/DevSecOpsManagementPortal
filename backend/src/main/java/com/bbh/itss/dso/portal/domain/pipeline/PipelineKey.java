package com.bbh.itss.dso.portal.domain.pipeline;

import lombok.Builder;

import java.time.Instant;

import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE;
import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.REVOKED;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@Builder(toBuilder = true)
public record PipelineKey(Long id, String value, KeyStatus status, Instant issuedAt, Instant revokedAt,
                          String revokeReason, Instant lastUsedAt) {

    public static final int REVOKE_REASON_MAX = 500;

    private static final int HINT_START = 8;
    private static final int HINT_END = 4;

    public PipelineKey {
        requireNonNull(value, "a key needs its value");
        requireNonNull(status, "a key needs its status");
        requireNonNull(issuedAt, "a key needs the time it was issued");
        revokeReason = trim(revokeReason);
        if ((status == REVOKED) != (revokedAt != null)) {
            throw new IllegalArgumentException("a key carries a revocation time exactly when it is revoked");
        }
    }

    static PipelineKey issue(String value, Instant now) {
        return builder().value(value).status(ACTIVE).issuedAt(now).build();
    }

    public static String normalize(String value) {
        return isBlank(value) ? "" : value.trim().toLowerCase(ROOT);
    }

    public String hint() {
        int length = value.length();
        return value.substring(0, min(HINT_START, length)) + "…" + value.substring(max(0, length - HINT_END));
    }

    public boolean isActive() {
        return status == ACTIVE;
    }

    public void requireActive() {
        if (!isActive()) {
            throw new SecurityException("The DevSecOps pipeline key was invalidated on " + revokedAt
                    + (revokeReason == null ? "" : ": " + revokeReason));
        }
    }

    PipelineKey revoke(String reason, Instant now) {
        return toBuilder().status(REVOKED).revokedAt(now).revokeReason(reason).build();
    }
}
