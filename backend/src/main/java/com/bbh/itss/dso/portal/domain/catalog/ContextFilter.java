package com.bbh.itss.dso.portal.domain.catalog;

import lombok.NoArgsConstructor;

import java.util.Arrays;

import static java.util.Locale.ROOT;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Whether a Liquibase context filter deploys the changesets marked context:test, by the algorithm the DevSecOps
 * library specifies: a filter that cannot be parsed counts as selecting them.
 */
@NoArgsConstructor(access = PRIVATE)
public final class ContextFilter {

    private static final String TRUE = ":TRUE";
    private static final String FALSE = ":FALSE";

    public static boolean selectsTest(String filter) {
        if (isBlank(filter)) {
            return true;
        }
        String text = filter;
        for (int close = text.indexOf(')'); close >= 0; close = text.indexOf(')')) {
            int open = text.lastIndexOf('(', close);
            if (open < 0 || close == open + 1) {
                return true;
            }
            text = text.substring(0, open) + " " + (matches(text.substring(open + 1, close)) ? TRUE : FALSE) + " "
                    + text.substring(close + 1);
        }
        return text.contains("(") || matches(text);
    }

    private static boolean matches(String expression) {
        return Arrays.stream(expression.replaceAll("\\s*,\\s*", " or ").split("\\s+or\\s+"))
                .anyMatch(part -> Arrays.stream(part.split("\\s+and\\s+")).allMatch(ContextFilter::item));
    }

    private static boolean item(String text) {
        String item = text.trim();
        if (item.matches("@\\s*(!|not ).+")) {
            item = item.substring(1).trim();
        }
        boolean negated = item.startsWith("!") || item.toLowerCase(ROOT).startsWith("not ");
        if (negated) {
            item = item.substring(item.startsWith("!") ? 1 : 4).trim();
        }
        if (item.startsWith("@")) {
            item = item.substring(1).trim();
        }
        boolean value = switch (item) {
            case "nocontexts" -> false;
            case TRUE -> true;
            case FALSE -> false;
            default -> item.equalsIgnoreCase("test");
        };
        return negated != value;
    }
}
