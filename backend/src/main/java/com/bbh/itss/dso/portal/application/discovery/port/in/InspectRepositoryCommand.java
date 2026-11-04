package com.bbh.itss.dso.portal.application.discovery.port.in;

public record InspectRepositoryCommand(
        String repositoryUrl,
        String branch,
        String credentialsId
) {
}
