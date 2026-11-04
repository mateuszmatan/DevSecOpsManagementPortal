package com.bbh.itss.dso.portal.application.discovery.port.out;

import com.bbh.itss.dso.portal.domain.discovery.RepoFileTree;

public interface BitbucketRepositoryPort {

    RepoFileTree fetchRepositoryTree(String repositoryUrl, String branch, String credentialsId);
}
