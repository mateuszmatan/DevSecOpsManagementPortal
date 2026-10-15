package com.bbh.itss.dso.portal.application.dsoconfig;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.RenderConfigUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.dsoconfig.DsoConfigBuilder;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@UseCase
@RequiredArgsConstructor
public class PipelineConfigService implements RenderConfigUseCase {

    private final PipelinesUseCase pipelines;
    private final ProductsUseCase products;
    private final ManageGlobalSettingsUseCase settings;

    @Override
    @WithoutTransaction
    public Map<String, Object> readByKey(String key) {
        return config(pipelines.get(pipelines.authorizeKey(key)));
    }

    @Override
    @ReadOnly
    public Map<String, Object> pipelineConfig(long pipelineId) {
        return config(pipelines.get(pipelineId));
    }

    @Override
    @ReadOnly
    public Map<String, Object> productConfig(long productId) {
        return builder().productConfig(products.get(productId));
    }

    @Override
    @ReadOnly
    public Map<String, Object> settingsConfig() {
        return builder().globalConfig();
    }

    private Map<String, Object> config(PipelineView view) {
        return builder().pipelineConfig(view.product(), view.service(), view.pipeline());
    }

    private DsoConfigBuilder builder() {
        return new DsoConfigBuilder(settings.current().values());
    }
}
