package com.bbh.itss.dso.portal.application.monitoring.port.in;

public interface MonitorPipelinesUseCase {

    MonitoringStatus status();

    MonitoringOverview overview();

    DepartmentPipelines department(long departmentId);

    ProductMonitoring product(long productId);

    PipelineMonitoring pipeline(long pipelineId, String range);

    PortfolioActivity activity(String range);
}
