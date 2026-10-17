package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.user.UserProfile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequestUri;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductsUseCase products;
    private final ActionLogUseCase actionLog;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<ProductSummaryView> list(@RequestParam(required = false) String search) {
        log.info("Listing products with filter: '{}'", search != null ? search : "");
        return products.list(search);
    }

    @GetMapping("/code-suggestion")
    public CodeSuggestion suggestCode(@RequestParam String name) {
        log.debug("Suggesting product code for name: '{}'", name);
        return new CodeSuggestion(products.suggestCode(name));
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable long id) {
        log.info("Fetching product details for id={}", id);
        return ProductDto.from(products.get(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductDto request,
                                             @RequestParam(required = false) PipelineType pipelineType) {
        log.info("Creating product with code='{}', name='{}', pipelineType={}", request.code(), request.name(), pipelineType);
        ProductDto created = ProductDto.from(products.create(request.toCommand(pipelineType)));
        log.info("Successfully created product id={}, code='{}'", created.id(), created.code());
        recordLog("CREATE_PRODUCT", created.name(), "Created product " + created.code() + " with " + (created.services() != null ? created.services().size() : 0) + " service(s)");
        return created(fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable long id, @Valid @RequestBody ProductDto request,
                             @RequestParam(required = false) PipelineType pipelineType) {
        log.info("Updating product id={} (code='{}', pipelineType={})", id, request.code(), pipelineType);
        ProductDto updated = ProductDto.from(products.update(id, request.toCommand(pipelineType)));
        log.info("Successfully updated product id={}, code='{}'", updated.id(), updated.code());
        recordLog("UPDATE_PRODUCT", updated.name(), "Updated product " + updated.code());
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("Deleting product id={}", id);
        products.delete(id);
        log.info("Successfully deleted product id={}", id);
        recordLog("DELETE_PRODUCT", "Product #" + id, "Deleted product #" + id);
    }

    private void recordLog(String actionType, String resourceName, String details) {
        try {
            UserProfile user = currentUserProvider.getCurrentUser();
            actionLog.log(
                    user != null ? user.username() : "system",
                    user != null ? user.displayName() : "System",
                    user != null ? user.email() : null,
                    user != null && !user.departmentNames().isEmpty() ? user.departmentNames().get(0) : null,
                    actionType,
                    "PRODUCT",
                    resourceName,
                    details,
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.warn("Failed to record action log for actionType={}, resourceName={}: {}", actionType, resourceName, e.getMessage());
        }
    }
}
