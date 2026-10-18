package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.CODE_PATTERN;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.CODE_RULE;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.NAME_MAX;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.OWNER_TEAM_MAX;

@JsonIgnoreProperties(value = {"id", "createdAt", "updatedAt"}, allowGetters = true, ignoreUnknown = true)
public record ProductDto(
        Long id,
        @NotBlank @Pattern(regexp = CODE_PATTERN, message = CODE_RULE) String code,
        @NotBlank @Size(max = NAME_MAX) String name,
        @Size(max = DESCRIPTION_MAX) String description,
        @Size(max = OWNER_TEAM_MAX) String ownerTeam,
        @Email @Size(max = 320) String contactEmail,
        Long departmentId,
        @Valid AppScanAccountDto appScan,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        @NotNull List<@NotNull @Valid ServiceDto> services) {

    @JsonCreator
    public static ProductDto create(
            @JsonProperty("id") Long id,
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("ownerTeam") String ownerTeam,
            @JsonProperty("contactEmail") String contactEmail,
            @JsonProperty("departmentId") Long departmentId,
            @JsonProperty("businessServiceId") Long businessServiceId,
            @JsonProperty("appScan") AppScanAccountDto appScan,
            @JsonProperty("version") Long version,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("services") List<ServiceDto> services) {
        return new ProductDto(id, code, name, description, ownerTeam, contactEmail,
                businessServiceId != null ? businessServiceId : departmentId,
                appScan, version, createdAt, updatedAt, services);
    }

    @JsonIgnore
    public Long businessServiceId() {
        return departmentId;
    }

    static ProductDto from(Product product) {
        return new ProductDto(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(), product.departmentId(),
                RecordMapper.map(product.appScanAccount(), AppScanAccountDto.class), product.version(),
                product.createdAt(), product.updatedAt(), product.services().stream().map(ServiceDto::from).toList());
    }

    ProductCommand toCommand(PipelineType pipelineType) {
        return new ProductCommand(version,
                new ProductDetails(code, name, description, ownerTeam, contactEmail, departmentId),
                RecordMapper.map(appScan, AppScanAccount.class), services.stream().map(ServiceDto::toDraft).toList(),
                pipelineType);
    }

    public record AppScanAccountDto(@Size(max = 200) String keyId, @Size(max = 200) String secretCredentialsId)
            implements Mirrors<AppScanAccount> {
    }
}
