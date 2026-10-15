package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import lombok.Getter;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.bbh.itss.dso.portal.domain.shared.Versions.requireCurrent;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.IntStream.range;

@Getter
public final class Product {

    private static final Comparator<Service> DISPLAY_ORDER =
            comparingInt(Service::displayOrder).thenComparing(Service::name);

    private final Long id;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private ProductDetails details;
    private AppScanAccount appScanAccount;
    private List<Service> services;

    private Product(Long id, ProductDetails details, AppScanAccount appScanAccount, List<Service> services, long version,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.details = details;
        this.appScanAccount = appScanAccount;
        this.services = services.stream().sorted(DISPLAY_ORDER).toList();
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Product create(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> services,
                                 ProductDirectory directory) {
        Product product = new Product(null, details, appScanAccount, List.of(), 0, null, null);
        product.change(details, appScanAccount, services, directory);
        return product;
    }

    public static Product restore(Long id, ProductDetails details, AppScanAccount appScanAccount, List<Service> services,
                                  long version, Instant createdAt, Instant updatedAt) {
        return new Product(id, requireNonNull(details), appScanAccount, services, version, createdAt, updatedAt);
    }

    public void update(Long expectedVersion, ProductDetails details, AppScanAccount appScanAccount,
                       List<ServiceDraft> services, ProductDirectory directory) {
        requireCurrent(expectedVersion, version);
        change(details, appScanAccount, services, directory);
    }

    private void change(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> drafts,
                        ProductDirectory directory) {
        requireNonNull(details, "a product needs its details");
        requireNonNull(drafts, "a product needs its list of services");
        new ProductRules(id, serviceIds(), directory).check(details, appScanAccount, drafts);
        this.details = details;
        this.appScanAccount = appScanAccount;
        this.services = range(0, drafts.size()).mapToObj(order -> drafts.get(order).place(order,
                details.code())).toList();
    }

    public Optional<Service> service(Long serviceId) {
        return services.stream().filter(service -> service.hasId(serviceId)).findFirst();
    }

    public Set<Long> serviceIds() {
        return services.stream().map(Service::id).filter(Objects::nonNull)
                .collect(toCollection(LinkedHashSet::new));
    }

    public void writeConfig(Service service, ConfigTree config) {
        service.settings().writeTo(config);
        appScanAccount.writeTo(config);
    }

    public String code() {
        return details.code();
    }

    public String name() {
        return details.name();
    }

    public String description() {
        return details.description();
    }

    public String ownerTeam() {
        return details.ownerTeam();
    }

    public String contactEmail() {
        return details.contactEmail();
    }

    public Long departmentId() {
        return details.departmentId();
    }
}
