package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.report.PipelineReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_PIPELINE_REPORT")
@Getter
@Setter
@NoArgsConstructor(access = PROTECTED)
public class PipelineReportEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(name = "PIPELINE_ID")
    private Long pipelineId;

    @Column(name = "PIPELINE_KEY")
    private String pipelineKey;

    @Column(name = "JOB_NAME")
    private String jobName;

    @Column(name = "BUILD_NUMBER")
    private Long buildNumber;

    @Enumerated(STRING)
    @Column(name = "RESULT")
    private RunResult result;

    @Column(name = "BRANCH")
    private String branch;

    @Column(name = "COMMIT_HASH")
    private String commitHash;

    @Column(name = "DURATION_SECONDS")
    private Long durationSeconds;

    @Column(name = "FINISHED_AT", nullable = false)
    private Instant finishedAt;

    @Column(name = "STAGES_TOTAL")
    private Long stagesTotal;

    @Column(name = "PASSED")
    private Long passed;

    @Column(name = "WARNED")
    private Long warned;

    @Column(name = "FAILED")
    private Long failed;

    @Column(name = "BLOCKED")
    private Long blocked;

    @Column(name = "SKIPPED")
    private Long skipped;

    @Lob
    @Column(name = "REPORT_HTML")
    private String reportHtml;

    public PipelineReportEntity(PipelineReport domain) {
        this.pipelineId = domain.pipelineId();
        this.pipelineKey = domain.pipelineKey();
        this.jobName = domain.jobName();
        this.buildNumber = domain.buildNumber();
        this.result = domain.result();
        this.branch = domain.branch();
        this.commitHash = domain.commitHash();
        this.durationSeconds = domain.durationSeconds();
        this.finishedAt = domain.finishedAt();
        this.stagesTotal = domain.stagesTotal();
        this.passed = domain.passed();
        this.warned = domain.warned();
        this.failed = domain.failed();
        this.blocked = domain.blocked();
        this.skipped = domain.skipped();
        this.reportHtml = domain.reportHtml();
    }

    public PipelineReport toDomain() {
        return new PipelineReport(
                id,
                pipelineId,
                pipelineKey,
                jobName,
                buildNumber,
                result,
                branch,
                commitHash,
                durationSeconds,
                finishedAt,
                stagesTotal,
                passed,
                warned,
                failed,
                blocked,
                skipped,
                reportHtml
        );
    }
}
