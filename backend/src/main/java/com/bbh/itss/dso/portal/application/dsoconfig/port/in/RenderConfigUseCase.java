package com.bbh.itss.dso.portal.application.dsoconfig.port.in;

import java.util.Map;

public interface RenderConfigUseCase {

    Map<String, Object> readByKey(String key);

    Map<String, Object> pipelineConfig(long pipelineId);

    Map<String, Object> productConfig(long productId);

    Map<String, Object> settingsConfig();
}
