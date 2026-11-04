package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;

/**
 * The hosts and the service of an Oracle thin JDBC URL in one of the forms the DevSecOps library deploys to:
 * EZConnect (Plus), SID or a descriptor, never with a user or a password.
 */
public record OracleUrl(List<String> hosts, String service) {

    public enum Problem { SECRET, FORM }

    private static final String THIN = "jdbc:oracle:thin:";
    private static final String PREFIX = THIN + "@";
    private static final String HOST = "[A-Za-z0-9][A-Za-z0-9.-]{0,252}";
    private static final String PORT = "([0-9]{1,5})";
    private static final Pattern EZCONNECT = Pattern.compile("(?://|tcps?://)?(" + HOST + "(?::[0-9]{1,5})?(?:,"
            + HOST + "(?::[0-9]{1,5})?)*)/([A-Za-z0-9_$#.-]{1,128})(?:\\?(.*))?");
    private static final Pattern HOST_PORT = Pattern.compile("(" + HOST + ")(?::" + PORT + ")?");
    private static final Pattern SID = Pattern.compile("(" + HOST + "):" + PORT + ":([A-Za-z0-9_$#]{1,30})");
    private static final Pattern DESCRIPTOR = Pattern.compile("(?i)\\(DESCRIPTION=[A-Za-z0-9=()._,:\\s-]+\\)");
    private static final Pattern PARAMETER_VALUE = Pattern.compile("\"?[A-Za-z0-9._=, -]{1,200}\"?");
    private static final List<String> PARAMETERS = List.of("ssl_server_dn_match", "ssl_server_cert_dn",
            "connect_timeout", "transport_connect_timeout", "retry_count", "retry_delay");
    private static final List<String> SECRETS = List.of("user=", "username=", "password=", "pwd=");

    public OracleUrl {
        hosts = hosts.stream().map(host -> host.trim().toLowerCase(ROOT)).toList();
        service = service == null ? "" : service.trim().toLowerCase(ROOT);
    }

    public static Optional<Problem> problemOf(String url) {
        return url == null || parse(url).isPresent() ? Optional.empty()
                : Optional.of(holdsSecret(url) ? Problem.SECRET : Problem.FORM);
    }

    public static Optional<OracleUrl> parse(String url) {
        if (url == null || !url.startsWith(PREFIX) || holdsSecret(url)) {
            return Optional.empty();
        }
        String connect = url.substring(PREFIX.length());
        return ezConnect(connect).or(() -> sid(connect)).or(() -> descriptor(connect));
    }

    /** Whether every host matches an entry, "host" or "host/SERVICE" with "*" for any run of characters. */
    public boolean allowedBy(List<String> entries) {
        return hosts.stream().allMatch(host -> entries.stream().anyMatch(entry -> {
            String[] parts = entry.trim().toLowerCase(ROOT).split("/", 2);
            return host.matches(glob(parts[0], "[a-z0-9.-]*"))
                    && (parts.length == 1 || service.matches(glob(parts[1], "[a-z0-9_$#.-]*")));
        }));
    }

    private static boolean holdsSecret(String url) {
        if (!url.startsWith(PREFIX)) {
            return url.startsWith(THIN) && url.indexOf('@') > THIN.length();
        }
        String lower = url.toLowerCase(ROOT).replaceAll("\\s+", "");
        return url.indexOf('@', PREFIX.length()) >= 0 || SECRETS.stream().anyMatch(lower::contains);
    }

    private static Optional<OracleUrl> ezConnect(String connect) {
        Matcher url = EZCONNECT.matcher(connect);
        if (!url.matches() || url.group(3) != null && !parameters(url.group(3))) {
            return Optional.empty();
        }
        List<Matcher> hosts = Stream.of(url.group(1).split(",")).map(HOST_PORT::matcher).filter(Matcher::matches)
                .toList();
        return hosts.stream().allMatch(host -> port(host.group(2)))
                ? Optional.of(new OracleUrl(hosts.stream().map(host -> host.group(1)).toList(), url.group(2)))
                : Optional.empty();
    }

    private static Optional<OracleUrl> sid(String connect) {
        Matcher url = SID.matcher(connect);
        return url.matches() && port(url.group(2)) ? Optional.of(new OracleUrl(List.of(url.group(1)), url.group(3)))
                : Optional.empty();
    }

    private static Optional<OracleUrl> descriptor(String connect) {
        if (!DESCRIPTOR.matcher(connect).matches() || !balanced(connect)) {
            return Optional.empty();
        }
        List<String> hosts = values(connect, "HOST");
        List<String> service = values(connect, "SERVICE_NAME");
        return hosts.isEmpty() ? Optional.empty() : Optional.of(new OracleUrl(hosts,
                (service.isEmpty() ? values(connect, "SID") : service).stream().findFirst().orElse(null)));
    }

    private static boolean parameters(String query) {
        return Arrays.stream(query.split("&", -1)).allMatch(parameter -> {
            int separator = parameter.indexOf('=');
            return separator > 0 && PARAMETERS.contains(parameter.substring(0, separator).toLowerCase(ROOT))
                    && PARAMETER_VALUE.matcher(parameter.substring(separator + 1)).matches();
        });
    }

    private static boolean port(String port) {
        return port == null || Integer.parseInt(port) >= 1 && Integer.parseInt(port) <= 65535;
    }

    private static boolean balanced(String text) {
        int depth = 0;
        for (char character : text.toCharArray()) {
            depth += character == '(' ? 1 : character == ')' ? -1 : 0;
            if (depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }

    private static List<String> values(String descriptor, String key) {
        return Pattern.compile("(?i)\\(" + key + "=([^()]*)\\)").matcher(descriptor.replaceAll("\\s+", ""))
                .results().map(match -> match.group(1)).toList();
    }

    private static String glob(String entry, String any) {
        return Arrays.stream(entry.split("\\*", -1)).map(Pattern::quote).collect(joining(any));
    }
}
