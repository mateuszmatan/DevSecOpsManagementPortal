package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.catalog.DatabaseSettings;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.trimmed;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The tools, the allowed databases and the Nexus repositories of every Liquibase service with database changes,
 * rendered under defaults only when set.
 */
@Builder(toBuilder = true)
public record DatabaseDefaults(String liquibaseHome, String liquibaseUrl, String liquibaseSha256,
                               Integer liquibaseJavaVersion, String oracleJdbcPath, String oracleJdbcUrl,
                               String oracleJdbcSha256, String utplsqlHome, String utplsqlUrl, String utplsqlSha256,
                               List<String> allowedTargets, String snapshotRepositoryId, String snapshotRepositoryUrl,
                               String releaseRepositoryId, String releaseRepositoryUrl) {

    public static final DatabaseDefaults NONE = builder().build();

    public DatabaseDefaults {
        liquibaseHome = trimToNull(liquibaseHome);
        liquibaseUrl = trimToNull(liquibaseUrl);
        liquibaseSha256 = trimToNull(liquibaseSha256);
        liquibaseJavaVersion = Integer.valueOf(17).equals(liquibaseJavaVersion) ? null : liquibaseJavaVersion;
        oracleJdbcPath = trimToNull(oracleJdbcPath);
        oracleJdbcUrl = trimToNull(oracleJdbcUrl);
        oracleJdbcSha256 = trimToNull(oracleJdbcSha256);
        utplsqlHome = trimToNull(utplsqlHome);
        utplsqlUrl = trimToNull(utplsqlUrl);
        utplsqlSha256 = trimToNull(utplsqlSha256);
        TreeSet<String> seen = new TreeSet<>(CASE_INSENSITIVE_ORDER);
        allowedTargets = trimmed(allowedTargets).stream().filter(seen::add).toList();
        snapshotRepositoryId = trimToNull(snapshotRepositoryId);
        snapshotRepositoryUrl = trimToNull(snapshotRepositoryUrl);
        releaseRepositoryId = trimToNull(releaseRepositoryId);
        releaseRepositoryUrl = trimToNull(releaseRepositoryUrl);
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("tools.liquibase.home", liquibaseHome)
                .set("tools.liquibase.url", liquibaseUrl)
                .set("tools.liquibase.sha256", liquibaseSha256)
                .set("tools.liquibase.javaVersion", liquibaseJavaVersion)
                .set("tools.oracleJdbc.path", oracleJdbcPath)
                .set("tools.oracleJdbc.url", oracleJdbcUrl)
                .set("tools.oracleJdbc.sha256", oracleJdbcSha256)
                .set("tools.utplsql.home", utplsqlHome)
                .set("tools.utplsql.url", utplsqlUrl)
                .set("tools.utplsql.sha256", utplsqlSha256)
                .set("database.allowedTargets", allowedTargets);
        writePackageTo(defaults);
    }

    public void writePackageTo(ConfigTree config) {
        config.set("database.package.snapshotRepository.id", snapshotRepositoryId)
                .set("database.package.snapshotRepository.url", snapshotRepositoryUrl)
                .set("database.package.releaseRepository.id", releaseRepositoryId)
                .set("database.package.releaseRepository.url", releaseRepositoryUrl);
    }

    public void validate(ValidationProblems problems) {
        String message = "is required with the download URL: the 64 lower-case hexadecimal digits of the file";
        if (liquibaseUrl != null) {
            problems.require("liquibaseSha256", liquibaseSha256, message);
        }
        if (oracleJdbcUrl != null) {
            problems.require("oracleJdbcSha256", oracleJdbcSha256, message);
        }
        if (utplsqlUrl != null) {
            problems.require("utplsqlSha256", utplsqlSha256, message);
        }
        LINES_4000.check(problems, "allowedTargets", allowedTargets);
    }

    /**
     * The first package setting a pipeline that delivers needs and that neither the service nor these defaults set:
     * the group ID and the snapshot repository, and for a release also the release repository.
     */
    public Optional<String> missingForDelivery(DatabaseSettings service, boolean release) {
        Map<String, String> needed = new LinkedHashMap<>();
        needed.put("group ID", service.groupId());
        needed.put("snapshot repository ID", getIfNull(service.snapshotRepositoryId(), snapshotRepositoryId));
        needed.put("snapshot repository URL", getIfNull(service.snapshotRepositoryUrl(), snapshotRepositoryUrl));
        if (release) {
            needed.put("release repository ID", getIfNull(service.releaseRepositoryId(), releaseRepositoryId));
            needed.put("release repository URL", getIfNull(service.releaseRepositoryUrl(), releaseRepositoryUrl));
        }
        return needed.entrySet().stream().filter(need -> need.getValue() == null).map(Map.Entry::getKey).findFirst();
    }
}
