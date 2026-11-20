package com.bbh.itss.dso.portal.application.bitbucket;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.bitbucket.port.in.DecoratePullRequestUseCase;
import com.bbh.itss.dso.portal.application.bitbucket.port.out.BitbucketPullRequestPort;
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.dso.portal.domain.bitbucket.PrDecorationResult;
import com.bbh.itss.dso.portal.domain.bitbucket.PrFinding;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            List<String> changed = changedFiles(request);
            Map<String, List<PrFinding>> unchanged = new LinkedHashMap<>();
            for (PrFinding finding : request.findings()) {
                String file = finding.file() == null ? "" : finding.file().trim();
                boolean hasLine = finding.line() != null && finding.line() > 0;
                String inPullRequest = file.isEmpty() ? null : changedPath(file, changed);
                if (!file.isEmpty() && hasLine && (changed == null || inPullRequest != null)) {
                    String target = inPullRequest != null ? inPullRequest : file;
                    String text = formatFindingComment(finding);
                    try {
                        pullRequestPort.addLineComment(request.repositoryUrl(), request.pullRequestId(),
                                request.credentialsId(), target, finding.line(), text);
                        lineComments++;
                    } catch (Exception ignored) {
                        try {
                            pullRequestPort.addComment(request.repositoryUrl(), request.pullRequestId(),
                                    request.credentialsId(), "`" + target + ":" + finding.line() + "`\n\n" + text);
                            generalComments++;
                        } catch (Exception secondary) {
                        }
                    }
                } else {
                    unchanged.computeIfAbsent(file, key -> new ArrayList<>()).add(finding);
                }
            }
            for (Map.Entry<String, List<PrFinding>> entry : unchanged.entrySet()) {
                try {
                    pullRequestPort.addComment(request.repositoryUrl(), request.pullRequestId(),
                            request.credentialsId(), formatUnchangedFileComment(entry.getKey(), entry.getValue()));
                    generalComments++;
                } catch (Exception ignored) {
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

    private List<String> changedFiles(PrDecorationRequest request) {
        try {
            return pullRequestPort.changedFiles(request.repositoryUrl(), request.pullRequestId(), request.credentialsId());
        } catch (Exception e) {
            return null;
        }
    }

    static String changedPath(String file, List<String> changed) {
        if (changed == null) {
            return null;
        }
        if (changed.contains(file)) {
            return file;
        }
        String wanted = file.replace('\\', '/').replaceFirst("^(?:\\./|/)+", "");
        List<String> matches = new ArrayList<>();
        for (String path : changed) {
            if (path.equals(wanted) || path.endsWith("/" + wanted) || wanted.endsWith("/" + path)) {
                matches.add(path);
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    static String formatUnchangedFileComment(String file, List<PrFinding> findings) {
        StringBuilder sb = new StringBuilder(file.isEmpty()
                ? "**[DevSecOps] Findings without a file of this repository**\n"
                : "**[DevSecOps] Findings in `" + file + "`** (this pull request does not change the file)\n");
        for (PrFinding finding : findings) {
            sb.append("\n---\n");
            if (!file.isEmpty()) {
                boolean hasLine = finding.line() != null && finding.line() > 0;
                sb.append("`").append(file).append(hasLine ? ":" + finding.line() : "").append("`\n\n");
            }
            sb.append(formatFindingComment(finding)).append("\n");
        }
        return sb.toString();
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
            sb.append("**Recommended fix:** ").append(finding.remediation());
        }
        return sb.toString();
    }
}
