package com.bbh.itss.dso.portal.application.pipeline;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageServiceTemplateUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.pipeline.IssuedKey;
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef;
import com.bbh.itss.dso.portal.domain.settings.ServiceTemplate;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.dso.portal.domain.pipeline.Pipeline.UNKNOWN_KEY;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineKey.normalize;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ;
import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static java.util.stream.Collectors.groupingBy;

@UseCase
@RequiredArgsConstructor
public class PipelineService implements PipelinesUseCase {

    private final PipelineRepositoryPort pipelines;
    private final ProductRepositoryPort products;
    private final ManageGlobalSettingsUseCase settings;
    private final ManageServiceTemplateUseCase template;
    private final KeyGenerator keys;
    private final Clock clock;

    @Override
    @ReadOnly
    public List<ServicePipelinesView> listForProduct(long productId) {
        Product product = products.load(productId).orElseThrow(() -> notFound("Product", productId));
        String jenkinsUrl = jenkinsUrl();
        Map<Long, List<PipelineView>> byService = pipelines.findByProductId(productId).stream()
                .map(pipeline -> PipelineView.of(product, pipeline, jenkinsUrl))
                .collect(groupingBy(view -> view.service().id()));
        return product.services().stream()
                .map(service -> new ServicePipelinesView(service, byService.getOrDefault(service.id(), List.of())))
                .toList();
    }

    @Override
    @ReadOnly
    public PipelineView get(long id) {
        return view(find(id));
    }

    @Override
    @ReadOnly
    public Product productOfService(long serviceId) {
        return products.findByServiceId(serviceId).orElseThrow(() -> notFound("Service", serviceId));
    }

    @Override
    public PipelineView create(long serviceId, PipelineType type, PipelineSettings settings) {
        Product product = productOfService(serviceId);
        Service service = product.service(serviceId).orElseThrow(() -> notFound("Service", serviceId));
        if (pipelines.existsForService(serviceId, type)) {
            throw new IllegalStateException("Service " + service.name() + " already has the " + type.label()
                    + " pipeline");
        }
        return PipelineView.of(product, create(product, service, type, settings), jenkinsUrl());
    }

    @Override
    public List<PipelineView> createMissing(long productId, List<Long> serviceIds, PipelineType type) {
        if (serviceIds.isEmpty()) {
            return List.of();
        }
        Product product = products.load(productId).orElseThrow(() -> notFound("Product", productId));
        String jenkinsUrl = jenkinsUrl();
        ServiceTemplate defaults = template.current().template();
        return serviceIds.stream()
                .map(serviceId -> product.service(serviceId)
                        .orElseThrow(() -> notFound("Service", serviceId)))
                .filter(service -> !pipelines.existsForService(service.id(), type))
                .map(service -> PipelineView.of(product, create(product, service, type,
                        defaults.pipelineSettings(product.code(), service.name(), type)), jenkinsUrl))
                .toList();
    }

    @Override
    public void requireRunnablePipelines(Product product) {
        for (Service service : product.services()) {
            for (PipelineType type : List.of(FULL, NEXUS_IQ)) {
                Optional<String> refusal = refusal(service, type);
                if (refusal.isPresent() && service.id() != null && pipelines.existsForService(service.id(), type)) {
                    throw new IllegalStateException(refusal.get());
                }
            }
        }
    }

    private Pipeline create(Product product, Service service, PipelineType type, PipelineSettings settings) {
        refusal(service, type).ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
        return pipelines.save(Pipeline.create(new ServiceRef(product.id(), service.id()), type, settings, keys,
                now()));
    }

    private static Optional<String> refusal(Service service, PipelineType type) {
        ServiceSettings configured = service.settings();
        if (type == NEXUS_IQ && configured.nexusIqApplications().isEmpty()) {
            return Optional.of("Service " + service.name()
                    + " has no Nexus IQ application, so its Nexus IQ pipeline would scan nothing");
        }
        if (type == FULL && configured.deployment().target() == VM
                && configured.urbanCodeApplications().stream().noneMatch(app -> app.deploysTo("DV"))) {
            return Optional.of("Service " + service.name() + " deploys to virtual machines but has no UrbanCode"
                    + " Deploy application for DV, so its Full pipeline would fail at the lower test region"
                    + " deployment");
        }
        return Optional.empty();
    }

    @Override
    public PipelineView update(long id, Long version, PipelineType type, PipelineSettings settings) {
        Pipeline pipeline = find(id);
        pipeline.reconfigure(version, type, settings);
        return view(pipelines.save(pipeline));
    }

    @Override
    public void delete(long id) {
        find(id);
        pipelines.delete(id);
    }

    @Override
    public PipelineView issueKey(long pipelineId) {
        Pipeline pipeline = lock(pipelineId);
        pipeline.issueKey(keys, now());
        return view(pipelines.save(pipeline));
    }

    @Override
    public PipelineView revokeKey(long pipelineId, String reason) {
        Pipeline pipeline = lock(pipelineId);
        pipeline.revokeActiveKey(reason, now());
        return view(pipelines.save(pipeline));
    }

    @Override
    public long authorizeKey(String keyValue) {
        String value = normalize(keyValue);
        IssuedKey issued = issuedKey(value);
        long pipelineId = issued.authorize();
        if (!pipelines.recordKeyUse(issued.key().id(), now())) {
            issuedKey(value).authorize();
        }
        return pipelineId;
    }

    private IssuedKey issuedKey(String value) {
        return pipelines.findKey(value).orElseThrow(() -> new NoSuchElementException(UNKNOWN_KEY));
    }

    private PipelineView view(Pipeline pipeline) {
        Product product = products.load(pipeline.service().productId())
                .orElseThrow(() -> notFound("Product", pipeline.service().productId()));
        return PipelineView.of(product, pipeline, jenkinsUrl());
    }

    private Pipeline find(long id) {
        return pipelines.load(id).orElseThrow(() -> notFound("Pipeline", id));
    }

    private Pipeline lock(long id) {
        return pipelines.loadForUpdate(id).orElseThrow(() -> notFound("Pipeline", id));
    }

    private String jenkinsUrl() {
        return settings.current().jenkinsUrl();
    }

    private Instant now() {
        return Timestamps.now(clock);
    }
}
