package com.bbh.itss.dso.portal.domain.catalog;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

/**
 * The value formats of the database changes and of their Library defaults, copied from the DevSecOps library, with
 * the library's sentences without the key and the value, because the portal shows them under the field.
 */
@NoArgsConstructor(access = PRIVATE)
public final class DatabaseFormats {

    public static final String OPTIONAL = "^$|";
    public static final String PATH = "^(?!(.*/)?\\.\\.(/|$))[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$";
    public static final String CHANGELOG_PATH_MESSAGE = "must be a relative path of letters, digits, \".\", \"_\" and"
            + " \"-\", separated by \"/\", without \"..\" and not starting with \"/\"";
    public static final String SEARCH_PATH_MESSAGE = "must be \".\" or a relative path inside the repository, without"
            + " \"..\"";
    public static final String SEARCH_ROOT_PATH_MESSAGE = "must be a relative path inside the search root, without"
            + " \"..\"";
    public static final String CHANGELOG_SUFFIX = "^.*\\.(xml|yaml|yml|json|sql)$";
    public static final String CHANGELOG_SUFFIX_MESSAGE = "must end with .xml, .yaml, .yml, .json or .sql";
    public static final String SCHEMA = "^[A-Za-z][A-Za-z0-9_$#]{0,127}$";
    public static final String SCHEMA_MESSAGE = "must be an Oracle schema name: a letter, then letters, digits, \"_\","
            + " \"$\" or \"#\", at most 128";
    public static final String IGNORE_OBJECT_MESSAGE = "must be an Oracle object name";
    public static final String CREDENTIALS = "^[A-Za-z0-9._-]{1,200}$";
    public static final String CREDENTIALS_MESSAGE = "must be a Jenkins credentials ID of letters, digits, \".\", \"_\""
            + " and \"-\" (at most 200)";
    public static final String CONTEXTS = "^[A-Za-z0-9_!@(), -]{0,200}$";
    public static final String CONTEXTS_MESSAGE = "may hold only letters, digits, blanks, \"_\", \"!\", \"@\", \"(\","
            + " \")\", \",\" and \"-\" (at most 200)";
    public static final String MAVEN = "^[A-Za-z0-9._-]{1,200}$";
    public static final String MAVEN_MESSAGE = "may hold only letters, digits, \".\", \"_\" and \"-\" (at most 200)";
    public static final String ABSOLUTE = "^/[^'\"\\x00-\\x1f\\x7f]{0,499}$";
    public static final String ABSOLUTE_MESSAGE = "must be an absolute path";
    public static final String MAVEN_HOME_MESSAGE = "must be an absolute path of a Maven installation";
    public static final String JDK_MESSAGE = "must be the absolute path of a JDK 17 or newer, such as"
            + " /usr/lib/jvm/java-17-openjdk";
    public static final String REPOSITORY_URL = "^https://[A-Za-z0-9.-]+(:[0-9]+)?(/[A-Za-z0-9._~/-]*)?$";
    public static final String REPOSITORY_URL_MESSAGE = "must be an https URL such as"
            + " https://nexus.bbh.com/repository/db-snapshots, without a user or a password";
    public static final String TOOL_URL = "^https://[A-Za-z0-9.-]+(:[0-9]+)?/[A-Za-z0-9._~/-]*\\.(tar\\.gz|tgz|zip|jar)$";
    public static final String TOOL_URL_MESSAGE = "must be an https URL of a .tar.gz, .tgz, .zip or .jar file, without"
            + " a user or a password";
    public static final String ARCHIVE = "^.*\\.(tar\\.gz|tgz|zip)$";
    public static final String ARCHIVE_MESSAGE = "must end with .tar.gz, .tgz or .zip";
    public static final String JAR = "^.*\\.jar$";
    public static final String JAR_MESSAGE = "must end with .jar";
    public static final String ZIP = "^.*\\.zip$";
    public static final String ZIP_MESSAGE = "must end with .zip";
    public static final String SHA256 = "^[0-9a-f]{64}$";
    public static final String SHA256_MESSAGE = "must be the 64 lower-case hexadecimal digits of the file";
    public static final String PARAMETER_NAME = "^[a-z][a-z0-9_.]{0,63}$";
    public static final String SECRET_NAME = "(?i)(liquibase_|dso_|jenkins).*"
            + "|.*(password|passwd|pwd|secret|token|credential|key|user).*";
    public static final String PARAMETER_VALUE = "^([\\x21-\\x7e]([\\x20-\\x7e]{0,498}[\\x21-\\x7e])?)?$";
    public static final String TARGET = "^[A-Za-z0-9*][A-Za-z0-9.*-]{0,252}(/[A-Za-z0-9_$#.*-]{1,128})?$";
    public static final String TARGET_MESSAGE = "must be \"host\" or \"host/SERVICE\" of letters, digits, \".\", \"-\""
            + " and \"*\"";
    public static final String ORACLE_VERSION = "^[0-9]{1,2}(\\.[0-9]{1,2})?$";
    public static final String ORACLE_VERSION_MESSAGE = "must be an Oracle version such as 19.0 or 23.0";
    public static final String UTPLSQL_PATHS = "^[A-Za-z0-9_$#.:,]{0,400}$";
    public static final String UTPLSQL_PATHS_MESSAGE = "may hold only letters, digits, \"_\", \"$\", \"#\", \".\", \":\""
            + " and \",\" (at most 400)";
}
