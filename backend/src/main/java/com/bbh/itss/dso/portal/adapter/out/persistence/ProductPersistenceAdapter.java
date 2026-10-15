package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductSummary;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.bbh.itss.dso.portal.adapter.out.persistence.AuditedEntity.current;
import static com.bbh.itss.dso.portal.adapter.out.persistence.Counts.perId;

@Component
@RequiredArgsConstructor
class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository products;
    private final ServiceJpaRepository services;
    private final DepartmentJpaRepository departments;

    @Override
    public Optional<Product> load(long id) {
        return products.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public Optional<Product> findByServiceId(long serviceId) {
        return services.findWithProductById(serviceId).map(service -> service.product().toDomain());
    }

    @Override
    public List<Product> findAll() {
        return products.findAllByOrderByNameAsc().stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public List<Product> findByDepartmentId(long departmentId) {
        return products.findByDepartmentIdOrderByNameAsc(departmentId).stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public List<ProductSummary> summaries() {
        return products.findAllByOrderByNameAsc().stream()
                .map(product -> RecordMapper.map(ProductSummary.class, product)).toList();
    }

    @Override
    public Map<Long, Long> servicesPerProduct() {
        return perId(services.countByProduct());
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = product.id() == null ? new ProductEntity()
                : current(products.findById(product.id()), product.version());
        entity.apply(product);
        if (product.id() != null) {
            entity.touch();
            Set<Long> kept = product.serviceIds();
            entity.services().stream().filter(service -> !kept.contains(service.id())).forEach(entity::removeService);
            products.flush();
            updateKeptServices(product, entity);
        }
        product.services().stream().filter(service -> service.id() == null)
                .forEach(service -> entity.addService().apply(service));
        return products.saveAndFlush(entity).toDomain();
    }

    private void updateKeptServices(Product product, ProductEntity entity) {
        Map<ServiceEntity, Service> kept = new LinkedHashMap<>();
        product.services().stream().filter(service -> service.id() != null)
                .forEach(service -> kept.put(entity.service(service.id()).orElseThrow(), service));
        List<ServiceEntity> moving = kept.entrySet().stream()
                .filter(entry -> !entry.getKey().name().equals(entry.getValue().name())).map(Map.Entry::getKey)
                .toList();
        if (!moving.isEmpty()) {
            moving.forEach(ServiceEntity::releaseName);
            products.flush();
        }
        kept.forEach(ServiceEntity::apply);
        products.flush();
    }

    @Override
    public void delete(long id) {
        products.findById(id).ifPresent(products::delete);
    }

    @Override
    public Optional<ProductIdentity> findProductByCode(String code) {
        return products.findByCodeIgnoreCase(code).map(ProductPersistenceAdapter::identity);
    }

    @Override
    public Optional<ProductIdentity> findProductByName(String name) {
        return products.findByNameIgnoreCase(name).map(ProductPersistenceAdapter::identity);
    }

    @Override
    public boolean departmentExists(long id) {
        return departments.existsById(id);
    }

    private static ProductIdentity identity(ProductEntity product) {
        return new ProductIdentity(product.id(), product.name());
    }
}
