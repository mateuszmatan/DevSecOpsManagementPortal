package com.bbh.itss.dso.portal.application.catalog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.OracleUrl;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bbh.itss.dso.portal.domain.catalog.ProductCode.suggest;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL;
import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

@UseCase
@RequiredArgsConstructor
public class ProductCatalogService implements ProductsUseCase {

    static final String NO_ALLOWED_TARGETS = "is set, but the Library defaults allow no database yet: the pipelines"
            + " deploy only to the databases the DevSecOps team allows in the Global Settings";
    static final String NOT_ALLOWED = "names a database that the Library defaults do not allow: ask the DevSecOps"
            + " team to add the test, RD or QC database to the Global Settings";

    private final ProductRepositoryPort products;
    private final PipelineCountsPort pipelineCounts;
    private final PipelinesUseCase pipelines;
    private final ManageGlobalSettingsUseCase settings;

    @Override
    @ReadOnly
    public List<ProductSummaryView> list(String search) {
        Map<Long, Long> serviceCounts = products.servicesPerProduct();
        Map<Long, Long> pipelines = pipelineCounts.pipelinesPerProduct();
        Map<Long, Long> active = pipelineCounts.activePipelinesPerProduct();
        return products.summaries().stream()
                .filter(product -> product.matches(search))
                .map(product -> new ProductSummaryView(product.id(), product.code(), product.name(),
                        product.description(), product.ownerTeam(), product.departmentId(), product.departmentName(),
                        serviceCounts.getOrDefault(product.id(), 0L), pipelines.getOrDefault(product.id(), 0L),
                        active.getOrDefault(product.id(), 0L), product.updatedAt()))
                .toList();
    }

    @Override
    @ReadOnly
    public Product get(long id) {
        return find(id);
    }

    @Override
    public Product create(ProductCommand command) {
        requireAllowedDatabases(command.services());
        Product saved = products.save(Product.create(command.details(), command.services(), products));
        return withPipelines(saved, Set.of(), command);
    }

    @Override
    public Product update(long id, ProductCommand command) {
        requireAllowedDatabases(command.services());
        Product product = find(id);
        Set<Long> known = product.serviceIds();
        product.update(command.version(), command.details(), command.services(), products);
        pipelines.requireRunnablePipelines(product);
        return withPipelines(products.save(product), known, command);
    }

    @Override
    public void delete(long id) {
        find(id);
        products.delete(id);
    }

    @Override
    @ReadOnly
    public String suggestCode(String name) {
        return suggest(name, products);
    }

    /** Refuses a database URL that the Library defaults do not allow; the domain refuses a URL of another form. */
    private void requireAllowedDatabases(List<ServiceDraft> services) {
        if (services.stream().noneMatch(service -> service.settings().changesDatabase())) {
            return;
        }
        List<String> allowed = settings.current().values().database().allowedTargets();
        ValidationProblems problems = new ValidationProblems();
        for (int i = 0; i < services.size(); i++) {
            String at = "services[" + i + "].databaseTargets[";
            services.get(i).settings().databaseTargets().forEach((target, value) -> OracleUrl.parse(value.url())
                    .filter(url -> !url.allowedBy(allowed))
                    .ifPresent(url -> problems.add(at + target + "].url",
                            allowed.isEmpty() ? NO_ALLOWED_TARGETS : NOT_ALLOWED)));
        }
        problems.throwIfAny();
    }

    private Product withPipelines(Product saved, Set<Long> known, ProductCommand command) {
        PipelineType type = command.pipelineType();
        pipelines.createMissing(saved.id(), saved.serviceIds().stream()
                .filter(serviceId -> type != null || !known.contains(serviceId))
                .toList(), getIfNull(type, FULL), command.agentLabels());
        return saved;
    }

    private Product find(long id) {
        return products.load(id).orElseThrow(() -> notFound("Product", id));
    }
}
