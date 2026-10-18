package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;

public interface ReadMonitoringTargetsUseCase {

    PlatformSettings platform();

    MonitoringTargets everything();

    MonitoringTargets ofDepartment(long departmentId);

    MonitoringTargets ofProduct(long productId);

    MonitoringTargets ofPipeline(long pipelineId);
}
