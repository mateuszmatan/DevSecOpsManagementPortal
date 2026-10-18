package com.bbh.itss.dso.portal.application.monitoring.port.out;

import com.bbh.itss.dso.portal.domain.monitoring.DashboardLink;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;

import java.util.List;

public interface DashboardLinksPort {

    List<DashboardLink> dashboards(PlatformSettings platform, MetricsTag tag, PipelineType type, int rangeDays);
}
