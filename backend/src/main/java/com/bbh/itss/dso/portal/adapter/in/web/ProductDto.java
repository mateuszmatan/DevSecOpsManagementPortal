package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
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

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.AGENT_LABEL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.AGENT_LABEL_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.CODE_PATTERN;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.CODE_RULE;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.NAME_MAX;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.OWNER_TEAM_MAX;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

@JsonIgnoreProperties(value = {"id", "createdAt", "updatedAt"}, allowGetters = true)
public record ProductDto(
        Long id,
        @NotBlank @Pattern(regexp = CODE_PATTERN, message = CODE_RULE) String code,
        @NotBlank @Size(max = NAME_MAX) String name,
        @Size(max = DESCRIPTION_MAX) String description,
        @Size(max = OWNER_TEAM_MAX) String ownerTeam,
        @Email @Size(max = 320) String contactEmail,
        Long departmentId,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        @NotNull List<@NotNull @Valid ServiceDto> services,
        @JsonProperty(access = WRITE_ONLY) @Size(max = 20)
        List<@Pattern(regexp = AGENT_LABEL, message = AGENT_LABEL_MESSAGE) String> agentLabels) {

    static ProductDto from(Product product) {
        return new ProductDto(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(), product.departmentId(), product.version(),
                product.createdAt(), product.updatedAt(), product.services().stream().map(ServiceDto::from).toList(),
                null);
    }

    ProductCommand toCommand(PipelineType pipelineType) {
        return new ProductCommand(version,
                new ProductDetails(code, name, description, ownerTeam, contactEmail, departmentId),
                services.stream().map(ServiceDto::toDraft).toList(), pipelineType, agentLabels);
    }
}
