package com.bbh.itss.dso.portal.adapter.out.bitbucket;

import com.bbh.itss.dso.portal.application.bitbucket.port.out.BitbucketPullRequestPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.Duration.ofSeconds;

@Component
@Slf4j
public class BitbucketHttpPullRequestAdapter implements BitbucketPullRequestPort {

    private static final Pattern SERVER_RE = Pattern.compile("^(https?://.+?)/(?:projects|users)/([^/]+)/repos/([^/?#]+).*$");
    private static final Pattern CLOUD_RE = Pattern.compile("^https?://(?:[^@/]+@)?(?:api\\.)?bitbucket\\.org/(?:2\\.0/repositories/)?([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    private final RestClient client;

    public BitbucketHttpPullRequestAdapter(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ofSeconds(4));
        requestFactory.setReadTimeout(ofSeconds(10));
        this.client = builder.requestFactory(requestFactory).build();
    }

    @Override
    public void addComment(String repositoryUrl, String pullRequestId, String credentialsId, String text) {
        if (repositoryUrl == null || pullRequestId == null || text == null || text.isBlank()) {
            return;
        }

        Matcher serverMatcher = SERVER_RE.matcher(repositoryUrl.trim());
        if (serverMatcher.matches()) {
            String baseUrl = serverMatcher.group(1);
            String projectKey = serverMatcher.group(2);
            String repoSlug = serverMatcher.group(3);
            String url = baseUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug + "/pull-requests/" + pullRequestId + "/comments";
            Map<String, Object> payload = Map.of("text", text);
            postJson(url, payload);
            return;
        }

        Matcher cloudMatcher = CLOUD_RE.matcher(repositoryUrl.trim());
        if (cloudMatcher.matches()) {
            String workspace = cloudMatcher.group(1);
            String repoSlug = cloudMatcher.group(2);
            String url = "https://api.bitbucket.org/2.0/repositories/" + workspace + "/" + repoSlug + "/pullrequests/" + pullRequestId + "/comments";
            Map<String, Object> payload = Map.of("content", Map.of("raw", text));
            postJson(url, payload);
        }
    }

    @Override
    public void addLineComment(String repositoryUrl, String pullRequestId, String credentialsId, String file, int line, String text) {
        if (repositoryUrl == null || pullRequestId == null || file == null || text == null || text.isBlank()) {
            return;
        }

        Matcher serverMatcher = SERVER_RE.matcher(repositoryUrl.trim());
        if (serverMatcher.matches()) {
            String baseUrl = serverMatcher.group(1);
            String projectKey = serverMatcher.group(2);
            String repoSlug = serverMatcher.group(3);
            String url = baseUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug + "/pull-requests/" + pullRequestId + "/comments";
            Map<String, Object> anchor = new HashMap<>();
            anchor.put("line", line);
            anchor.put("lineType", "ADDED");
            anchor.put("fileType", "TO");
            anchor.put("path", file);

            Map<String, Object> payload = new HashMap<>();
            payload.put("text", text);
            payload.put("anchor", anchor);
            postJson(url, payload);
            return;
        }

        Matcher cloudMatcher = CLOUD_RE.matcher(repositoryUrl.trim());
        if (cloudMatcher.matches()) {
            String workspace = cloudMatcher.group(1);
            String repoSlug = cloudMatcher.group(2);
            String url = "https://api.bitbucket.org/2.0/repositories/" + workspace + "/" + repoSlug + "/pullrequests/" + pullRequestId + "/comments";
            Map<String, Object> inline = Map.of("to", line, "path", file);
            Map<String, Object> payload = Map.of("content", Map.of("raw", text), "inline", inline);
            postJson(url, payload);
        }
    }

    @Override
    public List<String> changedFiles(String repositoryUrl, String pullRequestId, String credentialsId) {
        if (repositoryUrl == null || pullRequestId == null) {
            return null;
        }
        Matcher serverMatcher = SERVER_RE.matcher(repositoryUrl.trim());
        Matcher cloudMatcher = CLOUD_RE.matcher(repositoryUrl.trim());
        boolean cloud = !serverMatcher.matches() && cloudMatcher.matches();
        String first;
        if (serverMatcher.matches()) {
            first = serverMatcher.group(1) + "/rest/api/1.0/projects/" + serverMatcher.group(2) + "/repos/" + serverMatcher.group(3)
                    + "/pull-requests/" + pullRequestId + "/changes?limit=1000";
        } else if (cloud) {
            first = "https://api.bitbucket.org/2.0/repositories/" + cloudMatcher.group(1) + "/" + cloudMatcher.group(2)
                    + "/pullrequests/" + pullRequestId + "/diffstat?pagelen=500";
        } else {
            return null;
        }
        List<String> paths = new ArrayList<>();
        String url = first;
        try {
            for (int page = 0; page < 20 && url != null; page++) {
                Map<?, ?> body = client.get().uri(url).accept(MediaType.APPLICATION_JSON).retrieve().body(Map.class);
                if (body == null || !(body.get("values") instanceof List<?> values)) {
                    return page == 0 ? null : paths;
                }
                for (Object value : values) {
                    String path = changedPath(value, cloud);
                    if (path != null && !paths.contains(path)) {
                        paths.add(path);
                    }
                }
                if (cloud) {
                    url = body.get("next") instanceof String next ? next : null;
                } else {
                    url = Boolean.FALSE.equals(body.get("isLastPage")) && body.get("nextPageStart") != null
                            ? first + "&start=" + body.get("nextPageStart") : null;
                }
            }
            return paths;
        } catch (Exception e) {
            log.warn("Bitbucket files changed in pull request GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    static String changedPath(Object value, boolean cloud) {
        if (!(value instanceof Map<?, ?> change)) {
            return null;
        }
        Object place = cloud ? (change.get("new") != null ? change.get("new") : change.get("old")) : change.get("path");
        if (!(place instanceof Map<?, ?> map)) {
            return null;
        }
        Object path = cloud ? map.get("path") : map.get("toString");
        return path == null ? null : path.toString();
    }

    private void postJson(String url, Map<String, Object> payload) {
        try {
            client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Bitbucket pull request comment POST to {} failed: {}", url, e.getMessage());
        }
    }
}
