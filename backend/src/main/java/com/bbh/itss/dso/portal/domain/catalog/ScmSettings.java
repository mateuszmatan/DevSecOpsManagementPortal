package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BASIC;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.COMMAS_2000;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static org.apache.commons.lang3.ObjectUtils.anyNotNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record ScmSettings(String repositoryUrl, String credentialsId, BitbucketAuthType authType, BitbucketType type,
                          String targetBranch, String cloneUrl, List<String> reviewers, String apiUrl, String workspace,
                          String projectKey, String repoSlug) {

    public static final ScmSettings NONE = of(null, null);

    public ScmSettings {
        repositoryUrl = trimToNull(repositoryUrl);
        credentialsId = trimToNull(credentialsId);
        authType = getIfNull(authType, BASIC);
        targetBranch = trimToNull(targetBranch);
        cloneUrl = trimToNull(cloneUrl);
        reviewers = clean(reviewers);
        apiUrl = trimToNull(apiUrl);
        workspace = trimToNull(workspace);
        projectKey = trimToNull(projectKey);
        repoSlug = trimToNull(repoSlug);
    }

    public static ScmSettings of(String repositoryUrl, String credentialsId) {
        return builder().repositoryUrl(repositoryUrl).credentialsId(credentialsId).build();
    }

    public void writeTo(ConfigTree config) {
        if (repositoryUrl == null) {
            return;
        }
        config.set("scm.bitbucket.url", repositoryUrl)
                .set("scm.bitbucket.credentialsId", credentialsId)
                .set("scm.bitbucket.authType", authType)
                .set("scm.bitbucket.type", type)
                .set("scm.bitbucket.targetBranch", targetBranch)
                .set("scm.bitbucket.cloneUrl", cloneUrl)
                .set("scm.bitbucket.reviewers", reviewers)
                .set("scm.bitbucket.apiUrl", apiUrl)
                .set("scm.bitbucket.workspace", workspace)
                .set("scm.bitbucket.projectKey", projectKey)
                .set("scm.bitbucket.repoSlug", repoSlug);
    }

    public void validate(ValidationProblems problems) {
        if (repositoryUrl != null && credentialsId == null) {
            problems.add("credentialsId", "is required to push GoldenFix branches and open pull requests");
        }
        if (repositoryUrl == null && namesRepository()) {
            problems.add("repositoryUrl",
                    "is required when the Bitbucket API URL, workspace, project key or repository slug is set");
        }
        COMMAS_2000.check(problems, "reviewers", reviewers);
    }

    private boolean namesRepository() {
        return anyNotNull(apiUrl, workspace, projectKey, repoSlug);
    }
}
