package com.bbh.itss.dso.portal.application.catalog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
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

    private final ProductRepositoryPort products;
    private final PipelineCountsPort pipelineCounts;
    private final PipelinesUseCase pipelines;

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
        Product saved = products.save(Product.create(command.details(), command.appScan(), command.services(), products));
        return withPipelines(saved, Set.of(), command.pipelineType());
    }

    @Override
    public Product update(long id, ProductCommand command) {
        Product product = find(id);
        Set<Long> known = product.serviceIds();
        product.update(command.version(), command.details(), command.appScan(), command.services(), products);
        return withPipelines(products.save(product), known, command.pipelineType());
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

    private Product withPipelines(Product saved, Set<Long> known, PipelineType type) {
        pipelines.createMissing(saved.id(), saved.serviceIds().stream()
                .filter(serviceId -> type != null || !known.contains(serviceId))
                .toList(), getIfNull(type, FULL));
        return saved;
    }

    private Product find(long id) {
        return products.load(id).orElseThrow(() -> notFound("Product", id));
    }
}
