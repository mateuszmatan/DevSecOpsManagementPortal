package com.bbh.itss.dso.portal.application.bitbucket.port.in;

import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationResult;

public interface DecoratePullRequestUseCase {

    PrDecorationResult decorate(PrDecorationRequest request);
}
