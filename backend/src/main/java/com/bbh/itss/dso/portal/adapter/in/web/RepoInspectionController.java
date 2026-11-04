package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.discovery.port.in.InspectRepositoryCommand;
import com.bbh.itss.dso.portal.application.discovery.port.in.InspectRepositoryUseCase;
import com.bbh.itss.dso.portal.domain.discovery.DiscoveredStack;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/inspect-repo")
@RequiredArgsConstructor
public class RepoInspectionController {

    private final InspectRepositoryUseCase inspectRepositoryUseCase;

    @PostMapping
    public DiscoveredStack inspect(@Valid @RequestBody InspectRepoRequest request) {
        return inspectRepositoryUseCase.inspect(new InspectRepositoryCommand(
                request.repositoryUrl(),
                request.branch(),
                request.credentialsId()
        ));
    }
}
