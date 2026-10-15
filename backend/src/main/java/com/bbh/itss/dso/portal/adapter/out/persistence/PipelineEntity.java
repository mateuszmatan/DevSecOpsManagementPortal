package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_PIPELINE")
@NoArgsConstructor(access = PROTECTED)
public class PipelineEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "SERVICE_ID")
    private ServiceEntity service;

    @Enumerated(STRING)
    @Column(name = "PIPELINE_TYPE")
    private PipelineType type;

    private PipelineSettingsEmbeddable settings;

    @OneToMany(mappedBy = "pipeline", cascade = ALL, orphanRemoval = true)
    @OrderBy("issuedAt DESC, id DESC")
    private List<PipelineKeyEntity> keys = new ArrayList<>();

    PipelineEntity(ServiceEntity service, PipelineType type) {
        this.service = service;
        this.type = type;
    }

    Pipeline toDomain() {
        ServiceRef ref = new ServiceRef(service.product().id(), service.id());
        return Pipeline.restore(id, ref, type, RecordMapper.map(settings, PipelineSettings.class),
                keys.stream().map(PipelineKeyEntity::toDomain).toList(), version(), createdAt(), updatedAt());
    }

    void apply(Pipeline pipeline) {
        settings = RecordMapper.map(pipeline.settings(), PipelineSettingsEmbeddable.class);
        pipeline.keys().stream().filter(key -> key.id() != null)
                .forEach(key -> keys.stream().filter(entity -> key.id().equals(entity.id())).findFirst()
                        .orElseThrow().state(key.status(), key.revokedAt(), key.revokeReason()));
    }

    void addIssuedKeys(Pipeline pipeline) {
        List<PipelineKey> issued = pipeline.keys();
        for (int index = issued.size() - 1; index >= 0; index--) {
            PipelineKey key = issued.get(index);
            if (key.id() == null) {
                PipelineKeyEntity added = new PipelineKeyEntity(this, key.value(), key.issuedAt());
                added.state(key.status(), key.revokedAt(), key.revokeReason());
                keys.add(0, added);
            }
        }
    }

    @Embeddable
    public record PipelineSettingsEmbeddable(
            @Convert(converter = DelimitedListConverter.Commas.class) List<String> agentLabels,
            String extendedPipelineJob, String securityPipelineJob, String jenkinsJob, String description) {
    }
}
