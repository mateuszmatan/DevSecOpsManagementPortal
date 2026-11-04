package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.bitbucket.port.in.DecoratePullRequestUseCase;
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dso/bitbucket/pull-requests")
@RequiredArgsConstructor
public class BitbucketPrController {

    private final DecoratePullRequestUseCase decoratePullRequestUseCase;

    @PostMapping("/decorate")
    public PrDecorationResult decorate(@Valid @RequestBody PrDecorationRequest request) {
        return decoratePullRequestUseCase.decorate(request);
    }
}
