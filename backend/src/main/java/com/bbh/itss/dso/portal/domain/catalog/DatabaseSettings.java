package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.With;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.domain.catalog.BuildSettings.DEFAULT_SOURCE_DIR;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.ABSOLUTE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.JDK_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.PARAMETER_NAME;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.PARAMETER_VALUE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.PATH;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.SEARCH_PATH_MESSAGE;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseFormats.SECRET_NAME;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseTarget.QC;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseTarget.RD;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseTarget.TEST;
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.environmentMap;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.relativePath;
import static com.bbh.itss.dso.portal.domain.shared.Text.trimmed;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The database changes of a Liquibase service: the changelog, the checks, the package in Nexus and the utPLSQL tests.
 * They apply once the changelog file is set. A value equal to the library's default is kept as null, so it is not
 * written.
 */
@Builder(toBuilder = true)
public record DatabaseSettings(String changelogFile, String searchPath, String oracleVersion, String defaultSchema,
                               String liquibaseSchema, String contexts, String labels, List<String> parameters,
                               Boolean requireRollback, Boolean tag, Integer lockWaitMinutes, Boolean compileCheck,
                               Boolean recompile, List<String> ignoreObjects, List<String> ignoreFiles, String groupId,
                               @With String artifactId, Boolean nexusDelivery, String mvnPath,
                               String snapshotRepositoryId, String snapshotRepositoryUrl, String releaseRepositoryId,
                               String releaseRepositoryUrl, Boolean testReset, Boolean utplsql, String utplsqlPaths,
                               String utplsqlOwner, String utplsqlSourcePath, String utplsqlTestPath,
                               Integer utplsqlTimeoutMinutes) {

    public static final DatabaseSettings NONE = builder().build();

    static final String TEST_URL = "needs the JDBC URL of the test database";
    static final String SONAR_FOLDER = "sonar-scanner runs in that folder, and SonarQube analyses only the files under"
            + " it: set the directory to a folder that holds the package folder and the folder of the test packages, or"
            + " empty it";

    public DatabaseSettings {
        changelogFile = trimToNull(changelogFile);
        searchPath = relativePath(searchPath);
        oracleVersion = unlessDefault(trimToNull(oracleVersion), "19.0");
        defaultSchema = trimToNull(defaultSchema);
        liquibaseSchema = trimToNull(liquibaseSchema);
        contexts = unlessDefault(trimToNull(contexts), "!test");
        labels = trimToNull(labels);
        parameters = trimmed(parameters);
        requireRollback = unlessDefault(requireRollback, FALSE);
        tag = unlessDefault(tag, TRUE);
        lockWaitMinutes = unlessDefault(lockWaitMinutes, 5);
        compileCheck = unlessDefault(compileCheck, TRUE);
        recompile = unlessDefault(recompile, TRUE);
        ignoreObjects = trimmed(ignoreObjects);
        ignoreFiles = trimmed(ignoreFiles);
        groupId = trimToNull(groupId);
        artifactId = trimToNull(artifactId);
        nexusDelivery = unlessDefault(nexusDelivery, TRUE);
        mvnPath = trimToNull(mvnPath);
        snapshotRepositoryId = trimToNull(snapshotRepositoryId);
        snapshotRepositoryUrl = trimToNull(snapshotRepositoryUrl);
        releaseRepositoryId = trimToNull(releaseRepositoryId);
        releaseRepositoryUrl = trimToNull(releaseRepositoryUrl);
        testReset = unlessDefault(testReset, FALSE);
        utplsql = unlessDefault(utplsql, FALSE);
        utplsqlPaths = trimToNull(utplsqlPaths);
        utplsqlOwner = trimToNull(utplsqlOwner);
        utplsqlSourcePath = trimToNull(utplsqlSourcePath);
        utplsqlTestPath = trimToNull(utplsqlTestPath);
        utplsqlTimeoutMinutes = unlessDefault(utplsqlTimeoutMinutes, 30);
    }

    /** Null for a value equal to the library's default, so that it is neither stored nor written. */
    static <T> T unlessDefault(T value, T libraryDefault) {
        return libraryDefault.equals(value) ? null : value;
    }

    /** Whether a database setting or a database is set. */
    public boolean isSet(Map<DatabaseTarget, DatabaseTargetSettings> targets) {
        return !equals(NONE) || !targets.isEmpty();
    }

    /** Whether the pipelines deliver the package to Nexus, which is the library's default. */
    public boolean delivers() {
        return nexusDelivery == null;
    }

    public DatabaseSettings withDefaultArtifact(String serviceName) {
        return Objects.equals(serviceName, artifactId) ? withArtifactId(null) : this;
    }

    public void writeTo(ConfigTree config, Map<DatabaseTarget, DatabaseTargetSettings> targets) {
        boolean delivers = delivers();
        config.set("database.changelogFile", changelogFile)
                .set("database.searchPath", searchPath)
                .set("database.oracleVersion", oracleVersion)
                .set("database.defaultSchema", defaultSchema)
                .set("database.liquibaseSchema", liquibaseSchema)
                .set("database.contexts", contexts)
                .set("database.labels", labels)
                .set("database.parameters", environmentMap(parameters))
                .set("database.requireRollback", requireRollback)
                .set("database.tag", tag)
                .set("database.lockWaitMinutes", lockWaitMinutes)
                .set("database.compileCheck.enabled", compileCheck)
                .set("database.compileCheck.recompile", recompile)
                .set("database.compileCheck.ignoreObjects", ignoreObjects)
                .set("database.checks.ignoreFiles", ignoreFiles)
                .set("database.package.groupId", delivers ? groupId : null)
                .set("database.package.artifactId", artifactId)
                .set("database.package.nexusDelivery", nexusDelivery);
        if (delivers) {
            config.set("database.package.mvnPath", mvnPath)
                    .set("database.package.snapshotRepository.id", snapshotRepositoryId)
                    .set("database.package.snapshotRepository.url", snapshotRepositoryUrl)
                    .set("database.package.releaseRepository.id", releaseRepositoryId)
                    .set("database.package.releaseRepository.url", releaseRepositoryUrl);
        }
        targets.forEach((target, settings) -> config.set("database." + target.configKey(), settings.toConfig()));
        config.set("database.test.reset", testReset);
        if (utplsql != null) {
            config.set("database.test.utplsql.enabled", utplsql)
                    .set("database.test.utplsql.paths", utplsqlPaths)
                    .set("database.test.utplsql.owner", utplsqlOwner)
                    .set("database.test.utplsql.sourcePath", utplsqlSourcePath)
                    .set("database.test.utplsql.testPath", utplsqlTestPath)
                    .set("database.test.utplsql.timeoutMinutes", utplsqlTimeoutMinutes);
        }
    }

    /**
     * The rules that are not a single format, among them the JDK and the source folder of the build that Liquibase and
     * utPLSQL need, and the folder of the SonarQube analysis (tools.sonar.liquibase.dir); the problems of the
     * databases go to databaseTargets[...].
     */
    public void validate(ValidationProblems problems, Map<DatabaseTarget, DatabaseTargetSettings> targets,
                         BuildSettings build, String sonarFolder) {
        ValidationProblems database = problems.at("database");
        database.require("changelogFile", changelogFile, "is required once a database setting or database is set");
        validateBuild(problems.at("build"), build);
        if (changelogFile != null && packageFolder() == null && searchRoot(build) == null) {
            database.add("changelogFile", "has no folder and the search root is the repository root: put the"
                    + " changelog in a folder, or set the search folder or the source folder to the folder of the"
                    + " changelog");
        }
        if (changelogFile != null && sonarFolder != null) {
            validateSonarFolder(problems.at("sonar.command"), build, sonarFolder);
        }
        checkParameters(database, "parameters", parameters);
        LINES_4000.check(database, "ignoreObjects", ignoreObjects);
        LINES_4000.check(database, "ignoreFiles", ignoreFiles);
        targets.forEach((target, settings) -> validateTarget(problems.at("databaseTargets[" + target + "]"), settings));
        DatabaseTargetSettings test = targets.get(TEST);
        if (test != null && test.url() != null && Stream.of(RD, QC).map(targets::get).filter(Objects::nonNull)
                .anyMatch(other -> test.url().equalsIgnoreCase(Objects.toString(other.url(), "")))) {
            problems.add("databaseTargets[TEST].url", "is also the URL of the RD or QC database: the test database"
                    + " receives the test packages and must be a database of its own");
        }
        String qcContexts = targets.containsKey(QC) ? targets.get(QC).contexts() : null;
        if (ContextFilter.selectsTest(qcContexts != null ? qcContexts : Objects.toString(contexts, "!test"))) {
            (qcContexts == null ? database : problems.at("databaseTargets[QC]")).add("contexts",
                    "deploys the test changesets: QC needs a context filter such as \"!test\"");
        }
        if (test == null || test.url() == null) {
            if (utplsql != null) {
                database.add("utplsql", TEST_URL);
            }
            if (testReset != null) {
                database.add("testReset", TEST_URL);
            }
        }
    }

    /**
     * The folder sonar-scanner runs in: SonarQube analyses only the files under it, so it must hold the package folder
     * and, while the utPLSQL tests run, the folder of the test packages, as the library checks them (V3, V46). One
     * problem names every folder it leaves out. While the source folder or the search folder is not a folder of the
     * repository, only that folder is refused: the search root it would give is no folder to compare with.
     */
    private void validateSonarFolder(ValidationProblems problems, BuildSettings build, String sonarFolder) {
        if (!isFolder(sonarFolder)) {
            problems.add("directory", SEARCH_PATH_MESSAGE);
            return;
        }
        if (!isFolder(build.sourceDir()) || searchPath != null && !isFolder(searchPath)) {
            return;
        }
        String folder = join(sonarFolder);
        String sources = join(searchRoot(build), packageFolder());
        String tests = join(searchRoot(build), utplsqlTestPath);
        String leftOut = Stream.of(holds(folder, sources) ? null : "the package folder " + shown(sources),
                        utplsql == null || utplsqlTestPath == null || holds(folder, tests) ? null
                                : "the folder of the test packages " + shown(tests))
                .filter(Objects::nonNull).collect(joining(" and "));
        if (!leftOut.isEmpty()) {
            problems.add("directory", "does not hold " + leftOut + ": " + SONAR_FOLDER);
        }
    }

    /** Whether the path is "." or a relative path inside the repository without "..", as the library checks it (V3). */
    private static boolean isFolder(String path) {
        return DEFAULT_SOURCE_DIR.equals(path) || path.matches(PATH);
    }

    /** The folder Liquibase searches, the search folder inside the source folder; null for the repository root. */
    private String searchRoot(BuildSettings build) {
        return join(build.sourceDir(), searchPath);
    }

    /** The first folder of the changelog path, which the package holds; null for a changelog without a folder. */
    private String packageFolder() {
        int slash = changelogFile.indexOf('/');
        String first = slash > 0 ? changelogFile.substring(0, slash) : null;
        return ".".equals(first) ? null : first;
    }

    /** The folders joined as the library joins them, relative to the repository root; null for the root itself. */
    private static String join(String... folders) {
        return relativePath(Stream.of(folders).filter(Objects::nonNull).collect(joining("/")));
    }

    /** Whether the folder, null for the repository root, is the path or a folder above it. */
    private static boolean holds(String folder, String path) {
        return folder == null || folder.equals(path) || path != null && path.startsWith(folder + "/");
    }

    private static String shown(String folder) {
        return Objects.toString(folder, DEFAULT_SOURCE_DIR);
    }

    private static void validateBuild(ValidationProblems problems, BuildSettings build) {
        if (build.javaPath() == null && !build.autoSetup()) {
            problems.add("javaPath", "set the JDK path or enable automatic build tool setup: Liquibase and utPLSQL need"
                    + " Java 17 or newer");
        } else if (build.javaPath() != null && !build.javaPath().matches(ABSOLUTE)) {
            problems.add("javaPath", JDK_MESSAGE);
        }
        if (!isFolder(build.sourceDir())) {
            problems.add("sourceDir", SEARCH_PATH_MESSAGE);
        }
    }

    private static void validateTarget(ValidationProblems problems, DatabaseTargetSettings target) {
        OracleUrl.problemOf(target.url()).ifPresent(problem -> problems.add("url", problem == OracleUrl.Problem.SECRET
                ? "must not contain a user or a password: keep them in the Jenkins credentials of the credentials ID"
                : "must be an Oracle thin URL: jdbc:oracle:thin:@//host:1521/SERVICE, @host:1521:SID,"
                + " @tcps://host:2484/SERVICE?ssl_server_dn_match=true or @(DESCRIPTION=...)"));
        if (target.url() != null && target.credentialsId() == null) {
            problems.add("credentialsId", "is required with the JDBC URL: the ID of the Jenkins credentials of the"
                    + " database user");
        } else if (target.url() == null && target.credentialsId() != null) {
            problems.add("credentialsId", "is set without a JDBC URL");
        }
        checkParameters(problems, "parameters", target.parameters());
    }

    private static void checkParameters(ValidationProblems problems, String field, List<String> lines) {
        List<String[]> pairs = lines.stream().map(line -> line.split("=", 2)).toList();
        if (lines.size() > 50 || pairs.stream().anyMatch(pair -> pair.length < 2 || !pair[0].matches(PARAMETER_NAME))) {
            problems.add(field, "each line must be name=value, where the name starts with a lower-case letter and"
                    + " holds only lower-case letters, digits, \"_\" and \".\" (at most 64), and at most 50 lines");
        }
        if (pairs.stream().anyMatch(pair -> pair[0].matches(SECRET_NAME))) {
            problems.add(field, "holds a name that looks like a secret or a variable of the agent, which the library"
                    + " refuses in the changelog: changelog parameters are written into the package; keep secrets in"
                    + " Jenkins credentials");
        }
        if (pairs.stream().anyMatch(pair -> pair.length == 2
                && (!pair[1].matches(PARAMETER_VALUE) || pair[1].contains("${")))) {
            problems.add(field, "holds a value that is not printable ASCII of at most 500 characters, or that starts"
                    + " or ends with a blank, or holds \"${\"");
        }
        LINES_4000.check(problems, field, lines);
    }
}
