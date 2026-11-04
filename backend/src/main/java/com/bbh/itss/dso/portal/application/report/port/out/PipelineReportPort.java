package com.bbh.itss.dso.portal.application.report.port.out;

import com.bbh.itss.dso.portal.domain.report.PipelineReport;

import java.util.List;
import java.util.Optional;

public interface PipelineReportPort {

    PipelineReport save(PipelineReport report);

    Optional<PipelineReport> findLatestByPipelineId(Long pipelineId);

    Optional<PipelineReport> findByPipelineIdAndBuild(Long pipelineId, Long buildNumber);

    Optional<PipelineReport> findLatestByPipelineKey(String pipelineKey);

    Optional<PipelineReport> findLatestByJobName(String jobName);

    Optional<Long> findPipelineIdByKey(String key);

    Optional<Long> findPipelineIdByJobName(String jobName);

    List<PipelineReport> findRecentByPipelineId(Long pipelineId, int limit);
}
