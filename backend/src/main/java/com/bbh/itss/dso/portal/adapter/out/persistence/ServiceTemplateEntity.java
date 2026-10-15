package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.settings.ServiceTemplate;
import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.List;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_SERVICE_TEMPLATE")
@NoArgsConstructor(access = PROTECTED)
public class ServiceTemplateEntity extends AuditedEntity implements Persistable<Long> {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    private TemplateEmbeddable template;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return createdAt() == null;
    }

    StoredServiceTemplate toDomain() {
        return new StoredServiceTemplate(RecordMapper.map(template, ServiceTemplate.class), version(), updatedAt());
    }

    void apply(ServiceTemplate source) {
        template = RecordMapper.map(source, TemplateEmbeddable.class);
    }

    @Embeddable
    public record TemplateEmbeddable(
            @Convert(converter = DelimitedListConverter.Commas.class) List<String> agentLabels,
            String jenkinsJob, String gradleTasks, String gradleArtifact, String gradleScanPattern, String mavenTasks,
            String mavenArtifact, String mavenScanPattern, String flutterScanPattern, String deliveryTasks,
            String nexusIqApplication, String repositoryUrl, String bitbucketCredentialsId, String openShiftProject,
            String imageRegistry, String healthCheckUrl) {
    }
}
