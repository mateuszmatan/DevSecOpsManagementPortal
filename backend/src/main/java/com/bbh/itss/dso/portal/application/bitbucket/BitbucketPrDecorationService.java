package com.bbh.itss.dso.portal.application.bitbucket;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.bitbucket.port.in.DecoratePullRequestUseCase;
import com.bbh.itss.dso.portal.application.bitbucket.port.out.BitbucketPullRequestPort;
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationResult;
import com.bbh.itss.dso.portal.domain.bitbucket.PrFinding;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class BitbucketPrDecorationService implements DecoratePullRequestUseCase {

    private final BitbucketPullRequestPort pullRequestPort;

    @Override
    @WithoutTransaction
    public PrDecorationResult decorate(PrDecorationRequest request) {
        if (request == null || request.repositoryUrl() == null || request.pullRequestId() == null) {
            return new PrDecorationResult(null, 0, 0, "SKIPPED", "Missing repositoryUrl or pullRequestId");
        }

        int lineComments = 0;
        int generalComments = 0;

        if (request.findings() != null) {
            for (PrFinding finding : request.findings()) {
                if (finding.file() != null && !finding.file().isBlank() && finding.line() != null && finding.line() > 0) {
                    String text = formatFindingComment(finding);
                    try {
                        pullRequestPort.addLineComment(request.repositoryUrl(), request.pullRequestId(),
                                request.credentialsId(), finding.file(), finding.line(), text);
                        lineComments++;
                    } catch (Exception ignored) {
                        try {
                            pullRequestPort.addComment(request.repositoryUrl(), request.pullRequestId(),
                                    request.credentialsId(), "[" + finding.file() + ":" + finding.line() + "] " + text);
                            generalComments++;
                        } catch (Exception secondary) {
                        }
                    }
                }
            }
        }

        if (request.summary() != null && !request.summary().isBlank()) {
            try {
                pullRequestPort.addComment(request.repositoryUrl(), request.pullRequestId(),
                        request.credentialsId(), request.summary());
                generalComments++;
            } catch (Exception ignored) {
            }
        }

        return new PrDecorationResult(request.pullRequestId(), generalComments, lineComments, "SUCCESS",
                "Decorated pull request with " + lineComments + " line comment(s) and " + generalComments + " comment(s)");
    }

    private static String formatFindingComment(PrFinding finding) {
        StringBuilder sb = new StringBuilder();
        sb.append("**[").append(finding.tool() != null ? finding.tool() : "SECURITY").append("] ");
        if (finding.severity() != null) {
            sb.append(finding.severity()).append(" - ");
        }
        sb.append(finding.title() != null ? finding.title() : "Finding").append("**\n\n");
        if (finding.description() != null && !finding.description().isBlank()) {
            sb.append(finding.description()).append("\n\n");
        }
        if (finding.remediation() != null && !finding.remediation().isBlank()) {
            sb.append("**Suggested remediation:** ").append(finding.remediation());
        }
        return sb.toString();
    }
}
