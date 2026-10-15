package com.bbh.itss.dso.portal.application.monitoring.port.in;

public interface ReadMonitoringTargetsUseCase {

    MonitoringTargets everything();

    MonitoringTargets ofDepartment(long departmentId);

    MonitoringTargets ofProduct(long productId);

    MonitoringTargets ofPipeline(long pipelineId);
}
