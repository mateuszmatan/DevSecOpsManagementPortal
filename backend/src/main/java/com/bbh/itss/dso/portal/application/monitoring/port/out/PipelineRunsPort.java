package com.bbh.itss.dso.portal.application.monitoring.port.out;

import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PipelineRunsPort {

    boolean configured(PlatformSettings platform);

    void ping(PlatformSettings platform);

    LatestRuns latestRuns(PlatformSettings platform, Collection<MetricsTag> tags, Set<MetricsTag> sharedTags);

    List<PipelineRun> recentRuns(PlatformSettings platform, MetricsTag tag, String job, int days, int limit);

    Map<MetricsTag, List<DoraPoint>> doraPoints(PlatformSettings platform, Collection<MetricsTag> tags, String job,
                                                int days);
}
