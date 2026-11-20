package com.bbh.itss.dso.portal.application.bitbucket.port.out;

import java.util.List;

public interface BitbucketPullRequestPort {

    void addComment(String repositoryUrl, String pullRequestId, String credentialsId, String text);

    void addLineComment(String repositoryUrl, String pullRequestId, String credentialsId, String file, int line, String text);

    List<String> changedFiles(String repositoryUrl, String pullRequestId, String credentialsId);
}
