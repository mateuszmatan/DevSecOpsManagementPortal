package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.shared.Text.relativePath;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record VersionCatalogRepo(String repositoryUrl, String credentialsId, String branch, String path) {

    public static final VersionCatalogRepo NONE = builder().build();

    public VersionCatalogRepo {
        repositoryUrl = trimToNull(repositoryUrl);
        credentialsId = trimToNull(credentialsId);
        branch = trimToNull(branch);
        path = relativePath(path);
    }

    public boolean isPresent() {
        return repositoryUrl != null;
    }

    public void writeTo(ConfigTree config) {
        if (!isPresent()) {
            return;
        }
        config.set("goldenFix.versionCatalogRepo.url", repositoryUrl)
                .set("goldenFix.versionCatalogRepo.credentialsId", credentialsId)
                .set("goldenFix.versionCatalogRepo.branch", branch)
                .set("goldenFix.versionCatalogRepo.path", path);
    }

    public void validate(ValidationProblems problems) {
        if (!isPresent()) {
            return;
        }
        if (path != null && (path.matches("([/\\\\]|[A-Za-z]:).*") || List.of(path.split("[/\\\\]")).contains(".."))) {
            problems.add("path", "must be a path relative to the repository root, without '..'");
        }
    }
}
