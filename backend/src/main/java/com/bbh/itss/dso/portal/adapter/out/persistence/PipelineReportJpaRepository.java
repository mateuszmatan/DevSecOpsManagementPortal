package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PipelineReportJpaRepository extends JpaRepository<PipelineReportEntity, Long> {

    Optional<PipelineReportEntity> findFirstByPipelineIdOrderByFinishedAtDesc(Long pipelineId);

    Optional<PipelineReportEntity> findFirstByPipelineIdAndBuildNumber(Long pipelineId, Long buildNumber);

    Optional<PipelineReportEntity> findFirstByPipelineKeyOrderByFinishedAtDesc(String pipelineKey);

    Optional<PipelineReportEntity> findFirstByJobNameOrderByFinishedAtDesc(String jobName);

    List<PipelineReportEntity> findByPipelineIdOrderByFinishedAtDesc(Long pipelineId, Pageable pageable);

    @Query("SELECT k.pipeline.id FROM PipelineKeyEntity k WHERE k.value = :key AND k.status = 'ACTIVE'")
    Optional<Long> findPipelineIdByActiveKey(@Param("key") String key);

    @Query("SELECT p.id FROM PipelineEntity p WHERE p.settings.jenkinsJob = :jobName")
    Optional<Long> findPipelineIdByJenkinsJob(@Param("jobName") String jobName);
}
