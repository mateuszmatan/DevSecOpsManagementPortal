package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.report.port.out.PipelineReportPort;
import com.bbh.itss.dso.portal.domain.report.PipelineReport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PipelineReportPersistenceAdapter implements PipelineReportPort {

    private final PipelineReportJpaRepository repository;

    @Override
    public PipelineReport save(PipelineReport report) {
        PipelineReportEntity entity = new PipelineReportEntity(report);
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<PipelineReport> findLatestByPipelineId(Long pipelineId) {
        return repository.findFirstByPipelineIdOrderByFinishedAtDesc(pipelineId).map(PipelineReportEntity::toDomain);
    }

    @Override
    public Optional<PipelineReport> findByPipelineIdAndBuild(Long pipelineId, Long buildNumber) {
        return repository.findFirstByPipelineIdAndBuildNumber(pipelineId, buildNumber).map(PipelineReportEntity::toDomain);
    }

    @Override
    public Optional<PipelineReport> findLatestByPipelineKey(String pipelineKey) {
        return repository.findFirstByPipelineKeyOrderByFinishedAtDesc(pipelineKey).map(PipelineReportEntity::toDomain);
    }

    @Override
    public Optional<PipelineReport> findLatestByJobName(String jobName) {
        return repository.findFirstByJobNameOrderByFinishedAtDesc(jobName).map(PipelineReportEntity::toDomain);
    }

    @Override
    public Optional<Long> findPipelineIdByKey(String key) {
        return repository.findPipelineIdByActiveKey(key);
    }

    @Override
    public Optional<Long> findPipelineIdByJobName(String jobName) {
        return repository.findPipelineIdByJenkinsJob(jobName);
    }

    @Override
    public List<PipelineReport> findRecentByPipelineId(Long pipelineId, int limit) {
        return repository.findByPipelineIdOrderByFinishedAtDesc(pipelineId, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(PipelineReportEntity::toDomain)
                .toList();
    }
}
