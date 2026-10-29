package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * A map of an imported config file, read by dotted keys. A blank value, or one equal to the value the portal writes
 * anyway (the inherited one), counts as not set. A fallback, the defaults of a test stage, fills in the keys an entry
 * lacks. Whatever no reader takes becomes a note, so nothing in the file is left out without a word.
 */
final class ConfigInput {

    static final String UNKNOWN = "not imported: the portal has no such setting";
    static final String SECRET = "not imported: the portal keeps no secrets, only the IDs of Jenkins credentials";
    private static final Set<String> SECRETS = Set.of("keySecret", "token");

    private final String path;
    private final Map<?, ?> values;
    private final Map<?, ?> inherited;
    private final ConfigInput fallback;
    private final List<FieldProblem> notes;
    private final Set<String> read = new HashSet<>();

    ConfigInput(Map<?, ?> values, Map<?, ?> inherited, List<FieldProblem> notes) {
        this("", values, inherited, null, notes);
    }

    private ConfigInput(String path, Map<?, ?> values, Map<?, ?> inherited, ConfigInput fallback,
                        List<FieldProblem> notes) {
        this.path = path;
        this.values = values;
        this.inherited = inherited;
        this.fallback = fallback;
        this.notes = notes;
    }

    Object take(String key) {
        read.add(key);
        Object fallbackValue = fallback == null ? null : fallback.take(key);
        Object value = valueAt(values, key);
        if (isEmpty(value)) {
            return fallbackValue;
        }
        return same(value, valueAt(inherited, key)) ? null : value;
    }

    /** The map at the key, read on its own. */
    ConfigInput child(String key, Map<?, ?> inherited) {
        Object value = take(key);
        if (value != null && !(value instanceof Map<?, ?>)) {
            note(key, "not imported: expected a map");
        }
        return nested(key, value instanceof Map<?, ?> map ? map : Map.of(), inherited, null);
    }

    ConfigInput nested(String key, Map<?, ?> values, Map<?, ?> inherited, ConfigInput fallback) {
        return new ConfigInput(path + key + ".", values, inherited, fallback, notes);
    }

    /** The maps listed at the key, each read on its own: a text entry stands for the map {textKey: text}. */
    List<ConfigInput> entries(String key, String textKey, ConfigInput fallback) {
        Object value = take(key);
        if (value != null && !(value instanceof List<?>)) {
            note(key, "not imported: expected a list");
        }
        List<ConfigInput> entries = new ArrayList<>();
        List<?> list = value instanceof List<?> items ? items : List.of();
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            String at = key + "[" + i + "]";
            if (entry instanceof Map<?, ?> map) {
                entries.add(nested(at, map, Map.of(), fallback));
            } else if (textKey != null && !isEmpty(entry) && !(entry instanceof Collection<?>)) {
                entries.add(nested(at, Map.of(textKey, entry), Map.of(), fallback));
            } else if (!isEmpty(entry)) {
                note(at, "not imported: expected a map");
            }
        }
        return entries;
    }

    void note(String key, String message) {
        notes.add(new FieldProblem(path + key, message));
    }

    /** Notes every value nobody read and the portal would not write anyway: secrets as such, the rest as given. */
    void noteUnread(String message) {
        noteUnread("", values, message);
    }

    private void noteUnread(String prefix, Map<?, ?> map, String message) {
        map.forEach((name, value) -> {
            String key = prefix + name;
            if (value instanceof Map<?, ?> nested && !nested.isEmpty()) {
                noteUnread(key + ".", nested, message);
            } else if (!isEmpty(value) && !isRead(key) && !same(value, valueAt(inherited, key))) {
                note(key, SECRETS.contains(String.valueOf(name)) ? SECRET : message);
            }
        });
    }

    private boolean isRead(String key) {
        return read.stream().anyMatch(taken ->
                key.equals(taken) || key.startsWith(taken + ".") || taken.startsWith(key + "."));
    }

    /** The value at a dotted key; a number picks an entry of a list. */
    static Object valueAt(Object node, String key) {
        for (String name : key.split("\\.")) {
            if (node instanceof Map<?, ?> map) {
                node = map.get(name);
            } else if (node instanceof List<?> list && name.matches("\\d{1,3}")) {
                node = Integer.parseInt(name) < list.size() ? list.get(Integer.parseInt(name)) : null;
            } else {
                return null;
            }
        }
        return node;
    }

    private static boolean same(Object value, Object inherited) {
        return inherited != null && String.valueOf(value).equals(String.valueOf(inherited));
    }

    private static boolean isEmpty(Object value) {
        return value == null || value instanceof String text && isBlank(text)
                || value instanceof Collection<?> collection && collection.isEmpty()
                || value instanceof Map<?, ?> map && map.isEmpty();
    }
}
