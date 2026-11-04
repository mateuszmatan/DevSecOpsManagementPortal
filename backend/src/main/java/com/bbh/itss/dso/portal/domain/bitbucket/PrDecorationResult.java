package com.bbh.itss.dso.portal.domain.bitbucket;

public record PrDecorationResult(
        String pullRequestId,
        int commentsAdded,
        int lineCommentsAdded,
        String status,
        String message
) {
}
