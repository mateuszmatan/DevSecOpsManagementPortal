package com.bbh.itss.dso.portal.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record InspectRepoRequest(
        @NotBlank String repositoryUrl,
        String branch,
        String credentialsId
) {
}
