package com.bbh.itss.dso.portal.application.samm.port.in;

import com.bbh.itss.dso.portal.domain.samm.SammApplicationScore;
import com.bbh.itss.dso.portal.domain.samm.SammDepartmentScore;
import com.bbh.itss.dso.portal.domain.samm.SammOrganizationOverview;

import java.util.List;
import java.util.Optional;

public interface GetSammMaturityUseCase {

    SammOrganizationOverview overview();

    List<SammDepartmentScore> departmentScores();

    Optional<SammDepartmentScore> departmentScore(Long departmentId);

    Optional<SammApplicationScore> applicationScore(Long pipelineId);
}
