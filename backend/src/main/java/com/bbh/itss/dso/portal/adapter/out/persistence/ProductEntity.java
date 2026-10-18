package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.EmbeddedColumnNaming;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_PRODUCT")
@NoArgsConstructor(access = PROTECTED)
public class ProductEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Getter(PACKAGE)
    private Long id;

    private String code;

    @Getter(PACKAGE)
    private String name;

    private String description;
    private String ownerTeam;
    private String contactEmail;

    @Column(name = "DEPARTMENT_ID")
    private Long departmentId;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "DEPARTMENT_ID", insertable = false, updatable = false)
    private DepartmentEntity department;

    @EmbeddedColumnNaming("ASOC_%s")
    private AppScanAccountEmbeddable appScanAccount;

    @OneToMany(mappedBy = "product", cascade = ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, name ASC")
    private List<ServiceEntity> services = new ArrayList<>();

    String departmentName() {
        return department == null ? null : department.name();
    }

    Product toDomain() {
        return Product.restore(id, RecordMapper.map(ProductDetails.class, this),
                RecordMapper.map(appScanAccount, AppScanAccount.class),
                services.stream().map(ServiceEntity::toDomain).toList(), version(), createdAt(), updatedAt());
    }

    void apply(Product product) {
        ProductDetails details = product.details();
        code = details.code();
        name = details.name();
        description = details.description();
        ownerTeam = details.ownerTeam();
        contactEmail = details.contactEmail();
        departmentId = details.departmentId();
        appScanAccount = RecordMapper.map(product.appScanAccount(), AppScanAccountEmbeddable.class);
    }

    List<ServiceEntity> services() {
        return List.copyOf(services);
    }

    Optional<ServiceEntity> service(Long serviceId) {
        return services.stream().filter(service -> serviceId.equals(service.id())).findFirst();
    }

    ServiceEntity addService() {
        ServiceEntity service = new ServiceEntity(this);
        services.add(service);
        return service;
    }

    void removeService(ServiceEntity service) {
        services.remove(service);
    }

    @Embeddable
    public record AppScanAccountEmbeddable(String keyId, String secretCredentialsId) {
    }
}
