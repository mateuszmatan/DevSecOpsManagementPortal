package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;

import java.util.List;

public interface ProductsUseCase {

    List<ProductSummaryView> list(String search);

    Product get(long id);

    Product create(ProductCommand command);

    Product update(long id, ProductCommand command);

    void delete(long id);

    String suggestCode(String name);
}
