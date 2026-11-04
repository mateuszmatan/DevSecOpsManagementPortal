package com.bbh.itss.dso.portal.application.discovery.port.in;

import com.bbh.itss.dso.portal.domain.discovery.DiscoveredStack;

public interface InspectRepositoryUseCase {

    DiscoveredStack inspect(InspectRepositoryCommand command);
}
