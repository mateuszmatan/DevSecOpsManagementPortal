package com.bbh.itss.dso.portal.application.samm;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.report.port.out.PipelineReportPort;
import com.bbh.itss.dso.portal.application.samm.port.in.GetSammMaturityUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.report.PipelineReport;
import com.bbh.itss.dso.portal.domain.samm.SammApplicationScore;
import com.bbh.itss.dso.portal.domain.samm.SammCalculator;
import com.bbh.itss.dso.portal.domain.samm.SammDepartmentScore;
import com.bbh.itss.dso.portal.domain.samm.SammOrganizationOverview;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@UseCase
@RequiredArgsConstructor
public class SammMaturityService implements GetSammMaturityUseCase {

    private final ReadMonitoringTargetsUseCase monitoringTargets;
    private final DepartmentRepositoryPort departments;
    private final PipelineReportPort reports;
    private final PipelineRunsPort runs;
    private final Clock clock;

    @Override
    @ReadOnly
    public SammOrganizationOverview overview() {
        Instant now = clock.instant();
        MonitoringTargets targets = monitoringTargets.everything();
        List<Department> allDepts = departments.findAll();
        Map<Long, Department> deptMap = allDepts.stream()
                .collect(toMap(Department::id, Function.identity(), (a, b) -> a));

        LatestRuns latestRuns = runs.configured(targets.platform())
                ? runs.latestRuns(targets.platform(), targets.tags(), targets.sharedTags())
                : LatestRuns.none();

        List<SammApplicationScore> appScores = new ArrayList<>();
        for (PipelineView view : targets.pipelines()) {
            PipelineRun run = latestRuns.of(view.metricsTag(), view.pipeline());
            PipelineReport report = reports.findLatestByPipelineId(view.pipeline().id()).orElse(null);
            Department dept = deptMap.get(view.product().details().departmentId());
            appScores.add(SammCalculator.evaluateApplication(view.pipeline(), view.product(), view.service(), dept, report, run, now));
        }

        appScores.sort(comparing(SammApplicationScore::productName, CASE_INSENSITIVE_ORDER)
                .thenComparing(SammApplicationScore::serviceName, CASE_INSENSITIVE_ORDER));

        Map<Long, List<SammApplicationScore>> appsByDept = appScores.stream()
                .filter(a -> a.departmentId() != null)
                .collect(groupingBy(SammApplicationScore::departmentId));

        List<SammDepartmentScore> deptScores = allDepts.stream()
                .sorted(comparing(Department::name, CASE_INSENSITIVE_ORDER))
                .map(dept -> SammCalculator.evaluateDepartment(dept, appsByDept.getOrDefault(dept.id(), List.of())))
                .toList();

        return SammCalculator.evaluateOrganization(deptScores, appScores, now);
    }

    @Override
    @ReadOnly
    public List<SammDepartmentScore> departmentScores() {
        return overview().departments();
    }

    @Override
    @ReadOnly
    public Optional<SammDepartmentScore> departmentScore(Long departmentId) {
        if (departmentId == null) {
            return Optional.empty();
        }
        return overview().departments().stream()
                .filter(d -> d.departmentId().equals(departmentId))
                .findFirst();
    }

    @Override
    @ReadOnly
    public Optional<SammApplicationScore> applicationScore(Long pipelineId) {
        if (pipelineId == null) {
            return Optional.empty();
        }
        return overview().applications().stream()
                .filter(a -> a.pipelineId().equals(pipelineId))
                .findFirst();
    }
}
