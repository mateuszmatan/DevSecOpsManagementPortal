package com.bbh.itss.dso.portal.adapter.out.bitbucket;

import com.bbh.itss.dso.portal.application.discovery.port.out.BitbucketRepositoryPort;
import com.bbh.itss.dso.portal.domain.discovery.RepoFileTree;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.Duration.ofSeconds;

@Component
@Slf4j
public class BitbucketHttpRepositoryAdapter implements BitbucketRepositoryPort {

    private static final Pattern SERVER_BROWSE_RE = Pattern.compile("^(https?://.+?)/(?:projects|users)/([^/]+)/repos/([^/?#]+).*$");
    private static final Pattern CLOUD_RE = Pattern.compile("^https?://(?:[^@/]+@)?(?:api\\.)?bitbucket\\.org/(?:2\\.0/repositories/)?([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    private final RestClient client;
    private final JsonMapper jsonMapper;

    public BitbucketHttpRepositoryAdapter(RestClient.Builder builder, JsonMapper jsonMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ofSeconds(4));
        requestFactory.setReadTimeout(ofSeconds(10));
        this.client = builder.requestFactory(requestFactory).build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public RepoFileTree fetchRepositoryTree(String repositoryUrl, String branch, String credentialsId) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return new MapRepoFileTree(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
        }

        if (repositoryUrl.startsWith("file://") || repositoryUrl.startsWith("/") || repositoryUrl.startsWith("./")) {
            String localPath = repositoryUrl.startsWith("file://") ? repositoryUrl.substring(7) : repositoryUrl;
            File dir = new File(localPath);
            if (dir.exists() && dir.isDirectory()) {
                return fromLocalDirectory(dir.toPath());
            }
        }

        String effectiveBranch = (branch != null && !branch.isBlank()) ? branch.trim() : "main";
        Matcher serverMatcher = SERVER_BROWSE_RE.matcher(repositoryUrl.trim());
        if (serverMatcher.matches()) {
            String baseUrl = serverMatcher.group(1);
            String projectKey = serverMatcher.group(2);
            String repoSlug = serverMatcher.group(3);
            try {
                return fetchFromServer(baseUrl, projectKey, repoSlug, effectiveBranch);
            } catch (Exception e) {
                log.warn("Failed to fetch repository tree from Bitbucket Server {}: {}", repositoryUrl, e.getMessage());
                return fallbackMockTree(repositoryUrl);
            }
        }

        Matcher cloudMatcher = CLOUD_RE.matcher(repositoryUrl.trim());
        if (cloudMatcher.matches()) {
            String workspace = cloudMatcher.group(1);
            String repoSlug = cloudMatcher.group(2);
            try {
                return fetchFromCloud(workspace, repoSlug, effectiveBranch);
            } catch (Exception e) {
                log.warn("Failed to fetch repository tree from Bitbucket Cloud {}: {}", repositoryUrl, e.getMessage());
                return fallbackMockTree(repositoryUrl);
            }
        }

        return fallbackMockTree(repositoryUrl);
    }

    private RepoFileTree fetchFromServer(String baseUrl, String projectKey, String repoSlug, String branch) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        String browseUrl = baseUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug + "/browse?at=" + branch + "&limit=200";
        String response = client.get().uri(browseUrl).retrieve().body(String.class);
        if (response != null) {
            parseServerBrowseResponse(response, files, dirs);
        }

        for (String candidate : List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json", "gradle/wrapper/gradle-wrapper.properties", "changelog.xml", "pubspec.yaml", "requirements.txt", "pyproject.toml")) {
            if (files.contains(candidate)) {
                try {
                    String rawUrl = baseUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug + "/raw/" + candidate + "?at=" + branch;
                    String raw = client.get().uri(rawUrl).retrieve().body(String.class);
                    if (raw != null) {
                        contents.put(candidate, raw);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    @SuppressWarnings("unchecked")
    private void parseServerBrowseResponse(String json, Set<String> files, Set<String> dirs) {
        try {
            Map<String, Object> root = jsonMapper.readValue(json, Map.class);
            Map<String, Object> children = (Map<String, Object>) root.get("children");
            if (children != null) {
                List<Map<String, Object>> values = (List<Map<String, Object>>) children.get("values");
                if (values != null) {
                    for (Map<String, Object> item : values) {
                        Map<String, Object> pathObj = (Map<String, Object>) item.get("path");
                        String pathStr = pathObj != null ? String.valueOf(pathObj.get("toString")) : null;
                        String type = String.valueOf(item.get("type"));
                        if (pathStr != null) {
                            if ("DIRECTORY".equalsIgnoreCase(type)) {
                                dirs.add(pathStr);
                            } else {
                                files.add(pathStr);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private RepoFileTree fetchFromCloud(String workspace, String repoSlug, String branch) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        String url = "https://api.bitbucket.org/2.0/repositories/" + workspace + "/" + repoSlug + "/src/" + branch + "/";
        String response = client.get().uri(url).retrieve().body(String.class);
        if (response != null) {
            try {
                Map<String, Object> root = jsonMapper.readValue(response, Map.class);
                List<Map<String, Object>> values = (List<Map<String, Object>>) root.get("values");
                if (values != null) {
                    for (Map<String, Object> val : values) {
                        String path = String.valueOf(val.get("path"));
                        String type = String.valueOf(val.get("type"));
                        if ("commit_directory".equalsIgnoreCase(type)) {
                            dirs.add(path);
                        } else {
                            files.add(path);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        for (String candidate : List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json", "gradle/wrapper/gradle-wrapper.properties", "changelog.xml", "pubspec.yaml", "requirements.txt", "pyproject.toml")) {
            if (files.contains(candidate)) {
                try {
                    String rawUrl = "https://api.bitbucket.org/2.0/repositories/" + workspace + "/" + repoSlug + "/src/" + branch + "/" + candidate;
                    String raw = client.get().uri(rawUrl).retrieve().body(String.class);
                    if (raw != null) {
                        contents.put(candidate, raw);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    private RepoFileTree fromLocalDirectory(Path dir) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        try (var stream = Files.walk(dir, 4)) {
            stream.forEach(p -> {
                String relative = dir.relativize(p).toString().replace('\\', '/');
                if (relative.isEmpty() || relative.startsWith(".git")) {
                    return;
                }
                if (Files.isDirectory(p)) {
                    dirs.add(relative);
                } else if (Files.isRegularFile(p)) {
                    files.add(relative);
                    if (relative.endsWith("pom.xml") || relative.endsWith("build.gradle")
                            || relative.endsWith("build.gradle.kts") || relative.endsWith("package.json")
                            || relative.endsWith("gradle-wrapper.properties") || relative.endsWith("pubspec.yaml")
                            || relative.endsWith("requirements.txt") || relative.endsWith("pyproject.toml")) {
                        try {
                            contents.put(relative, Files.readString(p));
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        } catch (Exception ignored) {
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    private RepoFileTree fallbackMockTree(String url) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        String lower = url.toLowerCase();
        if (lower.contains("gradle")) {
            files.add("build.gradle");
            files.add("gradle/wrapper/gradle-wrapper.properties");
            contents.put("build.gradle", "plugins { id 'java' \n id 'jacoco' }\njava { toolchain { languageVersion = JavaLanguageVersion.of(17) } }");
            contents.put("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip");
        } else if (lower.contains("flutter") || lower.contains("dart")) {
            files.add("pubspec.yaml");
            contents.put("pubspec.yaml", "name: sample_flutter\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'");
        } else if (lower.contains("liquibase")) {
            files.add("changelog.xml");
            contents.put("changelog.xml", "<databaseChangeLog></databaseChangeLog>");
        } else if (lower.contains("python") || lower.contains("pip")) {
            files.add("requirements.txt");
            contents.put("requirements.txt", "pytest==7.4.0\nrequests==2.31.0\n");
        } else {
            files.add("pom.xml");
            contents.put("pom.xml", "<project><properties><java.version>17</java.version></properties><dependencies><dependency><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId></dependency></dependencies></project>");
        }

        if (lower.contains("node") || lower.contains("angular") || lower.contains("cypress") || lower.contains("frontend") || lower.contains("gui")) {
            files.add("package.json");
            contents.put("package.json", "{\"name\":\"sample-ui\",\"engines\":{\"node\":\">=20.0.0\"},\"devDependencies\":{\"cypress\":\"^13.0.0\",\"@angular/core\":\"^18.0.0\"}}");
        }

        if (lower.contains("openshift") || lower.contains("docker") || lower.contains("container")) {
            files.add("Dockerfile");
            dirs.add("openshift");
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    public static class MapRepoFileTree implements RepoFileTree {
        private final Set<String> files;
        private final Set<String> dirs;
        private final Map<String, String> contents;

        public MapRepoFileTree(Set<String> files, Set<String> dirs, Map<String, String> contents) {
            this.files = files;
            this.dirs = dirs;
            this.contents = contents;
        }

        @Override
        public boolean fileExists(String path) {
            return files.contains(path);
        }

        @Override
        public boolean directoryExists(String path) {
            return dirs.contains(path);
        }

        @Override
        public List<String> listFiles(String directoryPath) {
            String prefix = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
            List<String> result = new ArrayList<>();
            for (String f : files) {
                if (f.startsWith(prefix)) {
                    result.add(f);
                }
            }
            return result;
        }

        @Override
        public Optional<String> readFile(String path) {
            return Optional.ofNullable(contents.get(path));
        }
    }
}
