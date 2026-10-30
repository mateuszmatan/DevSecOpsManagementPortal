package com.bbh.itss.dso.portal.domain.shared;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.Strings.CS;

@NoArgsConstructor(access = PRIVATE)
public final class Text {

    public static boolean isUrl(String value) {
        return CS.startsWithAny(value, "http://", "https://");
    }

    public static boolean matches(String search, String... values) {
        String needle = trimToEmpty(search).toLowerCase(ROOT);
        return needle.isEmpty() || Stream.of(values).anyMatch(value -> CS.contains(lowerCase(value, ROOT), needle));
    }

    public static int bytes(String value) {
        return value.getBytes(UTF_8).length;
    }

    public static String abbreviateBytes(String text, int maxBytes) {
        if (text == null || bytes(text) <= maxBytes) {
            return text;
        }
        int[] used = {0};
        return text.codePoints().takeWhile(point -> (used[0] += bytesOf(point)) <= maxBytes - 3)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().stripTrailing() + "...";
    }

    private static int bytesOf(int codePoint) {
        return codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
    }

    public static List<String> clean(List<String> values) {
        return trimmed(values).stream().distinct().toList();
    }

    /**
     * A path relative to the repository root without its "." and empty folders, so "./web/" is "web" and "." is null,
     * the root itself. A path that starts at "/" is kept as it is for validation to refuse.
     */
    public static String relativePath(String path) {
        String trimmed = trimToNull(path);
        if (trimmed == null || trimmed.startsWith("/")) {
            return trimmed;
        }
        return trimToNull(Stream.of(trimmed.split("/")).filter(folder -> !folder.isEmpty() && !folder.equals("."))
                .collect(joining("/")));
    }

    public static List<String> relativePaths(List<String> paths) {
        return clean(emptyIfNull(paths).stream().map(Text::relativePath).toList());
    }

    public static List<String> trimmed(List<String> values) {
        return emptyIfNull(values).stream().filter(StringUtils::isNotBlank).map(String::trim).toList();
    }
}
