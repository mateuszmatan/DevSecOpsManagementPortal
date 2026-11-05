package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

final class ProductRules {

    private final Long productId;
    private final Set<Long> ownServiceIds;
    private final ProductDirectory directory;

    ProductRules(Long productId, Set<Long> ownServiceIds, ProductDirectory directory) {
        this.productId = productId;
        this.ownServiceIds = Set.copyOf(ownServiceIds);
        this.directory = requireNonNull(directory, "the product rules need the product directory");
    }

    void check(ProductDetails details, List<ServiceDraft> services) {
        requireIdentity(details, services);
        details.requireUnique(productId, directory);

        ValidationProblems problems = new ValidationProblems();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < services.size(); i++) {
            ServiceDraft service = services.get(i);
            ValidationProblems at = problems.at("services[" + i + "]");
            service.settings().validate(at);
            at.fits("description", service.description(), ServiceDraft.DESCRIPTION_MAX);
            if (service.id() != null && !ownServiceIds.contains(service.id())) {
                at.add("id", "service " + service.id() + " does not belong to this product");
            }
            if (!names.add(service.name().trim().toLowerCase(ROOT))) {
                at.add("name", "another service of this product already uses this name");
            }
        }
        validateCrossServiceScanIds(services, problems);
        problems.throwIfAny();
    }

    private void validateCrossServiceScanIds(List<ServiceDraft> services, ValidationProblems problems) {
        for (int i = 0; i < services.size(); i++) {
            ServiceDraft a = services.get(i);
            String repoA = normalizeRepo(a.settings().scm().repositoryUrl());
            String sastA = a.settings().appScan().applicationId();
            String sonarA = a.settings().sonar().projectKey();

            for (int j = i + 1; j < services.size(); j++) {
                ServiceDraft b = services.get(j);
                String repoB = normalizeRepo(b.settings().scm().repositoryUrl());
                boolean diffRepo = !repoA.isEmpty() && !repoB.isEmpty() && !repoA.equalsIgnoreCase(repoB);
                if (diffRepo) {
                    String sastB = b.settings().appScan().applicationId();
                    if (sastA != null && !sastA.isBlank() && sastA.equalsIgnoreCase(sastB)) {
                        problems.at("services[" + j + "].appScan")
                                .add("applicationId", "services in different Bitbucket repositories cannot share the same AppScan application ID (" + sastB + " is already used by " + a.name() + ")");
                    }
                    String sonarB = b.settings().sonar().projectKey();
                    if (sonarA != null && !sonarA.isBlank() && sonarA.equalsIgnoreCase(sonarB)) {
                        problems.at("services[" + j + "].sonar")
                                .add("projectKey", "services in different Bitbucket repositories cannot share the same SonarQube project key (" + sonarB + " is already used by " + a.name() + ")");
                    }
                    for (NexusIqApplication niqA : a.settings().nexusIqApplications()) {
                        String appA = niqA.application();
                        if (appA == null || appA.isBlank()) continue;
                        for (NexusIqApplication niqB : b.settings().nexusIqApplications()) {
                            String appB = niqB.application();
                            if (appB != null && !appB.isBlank() && appA.equalsIgnoreCase(appB)) {
                                problems.at("services[" + j + "].nexusIq")
                                        .add("applications", "services in different Bitbucket repositories cannot share the same Nexus IQ application (" + appB + " is already used by " + a.name() + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    private static String normalizeRepo(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String u = url.trim().toLowerCase(ROOT);
        if (u.endsWith(".git")) {
            u = u.substring(0, u.length() - 4);
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private void requireIdentity(ProductDetails details, List<ServiceDraft> services) {
        ValidationProblems problems = details.validate(new ValidationProblems(), directory);
        for (int i = 0; i < services.size(); i++) {
            if (isBlank(services.get(i).name())) {
                problems.add("services[" + i + "].name", "must not be blank");
            }
        }
        problems.throwIfAny();
    }
}
