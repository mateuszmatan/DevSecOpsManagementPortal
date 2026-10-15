package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public record PipelineResponse(Long id, Long productId, String productCode, String productName, Long serviceId,
                               String serviceName, PipelineType type, String entryPoint, List<String> agentLabels,
                               String extendedPipelineJob, String securityPipelineJob, String jenkinsJob,
                               String jenkinsJobUrl, String description, boolean enabled, KeyResponse activeKey,
                               String influxProjectTag, String influxEnv, long version, Instant createdAt,
                               Instant updatedAt, List<KeyResponse> keys) {

    public static PipelineResponse summary(PipelineView view) {
        return of(view, KeyResponse::from, null);
    }

    public static PipelineResponse withKeys(PipelineView view) {
        return of(view, KeyResponse::from, view.pipeline().keys().stream().map(KeyResponse::from).toList());
    }

    public static PipelineResponse monitored(PipelineView view) {
        return of(view, KeyResponse::masked, List.of());
    }

    private static PipelineResponse of(PipelineView view, Function<PipelineKey, KeyResponse> activeKey,
                                       List<KeyResponse> keys) {
        Pipeline pipeline = view.pipeline();
        PipelineSettings settings = pipeline.settings();
        MetricsTag tag = view.metricsTag();
        return new PipelineResponse(pipeline.id(), view.product().id(), view.product().code(), view.product().name(),
                view.service().id(), view.service().name(), pipeline.type(), pipeline.type().entryPoint(),
                settings.agentLabels(), settings.extendedPipelineJob(), settings.securityPipelineJob(),
                settings.jenkinsJob(), view.jenkinsJobUrl(), settings.description(), pipeline.isEnabled(),
                pipeline.activeKey().map(activeKey).orElse(null), tag.project(), tag.env(), pipeline.version(),
                pipeline.createdAt(), pipeline.updatedAt(), keys);
    }

    public record KeyResponse(Long id, String value, String hint, KeyStatus status, Instant issuedAt, Instant revokedAt,
                              String revokeReason, Instant lastUsedAt) {

        static KeyResponse from(PipelineKey key) {
            return of(key, key.isActive() ? key.value() : null);
        }

        static KeyResponse masked(PipelineKey key) {
            return of(key, null);
        }

        private static KeyResponse of(PipelineKey key, String value) {
            return new KeyResponse(key.id(), value, key.hint(), key.status(), key.issuedAt(), key.revokedAt(),
                    key.revokeReason(), key.lastUsedAt());
        }
    }
}
