package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses an imported config file: JSON when it starts with a brace, YAML otherwise. The YAML loader builds plain maps,
 * lists and scalars only, keeps versions such as 1.10 and dates as text, and refuses large, deeply nested or
 * alias-heavy documents. An error names the line, the column and the parser's problem, never the source line, which
 * may hold a secret.
 */
final class ConfigFileParser {

    static final int MAX_LENGTH = 262_144;

    private ConfigFileParser() {
    }

    static Object parse(String content, JsonMapper json) {
        if (content.stripLeading().startsWith("{")) {
            try {
                return json.readValue(content, Object.class);
            } catch (JacksonException e) {
                TokenStreamLocation location = e.getLocation();
                throw location == null ? invalid(1, 1, "is not valid JSON")
                        : invalid(location.getLineNr(), location.getColumnNr(), "is not valid JSON");
            }
        }
        try {
            return yaml().load(content);
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark();
            throw mark == null ? invalid(1, 1, e.getProblem())
                    : invalid(mark.getLine() + 1, mark.getColumn() + 1, e.getProblem());
        } catch (YAMLException e) {
            throw InvalidRequestException.of("content",
                    "exceeds the import limits for size, nesting or aliases, or holds characters YAML does not allow");
        }
    }

    static InvalidRequestException invalid(int line, int column, String problem) {
        return InvalidRequestException.of("content", "line %d, column %d: %s".formatted(line, column, problem));
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(MAX_LENGTH);
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(30);
        options.setAllowRecursiveKeys(false);
        DumperOptions dumper = new DumperOptions();
        return new Yaml(new SafeConstructor(options), new Representer(dumper), dumper, options, new PlainScalars());
    }

    /** The YAML 1.1 scalar types without floats and timestamps. */
    private static final class PlainScalars extends Resolver {

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, BOOL, "yYnNtTfFoO", 10);
            addImplicitResolver(Tag.INT, INT, "-+0123456789");
            addImplicitResolver(Tag.MERGE, MERGE, "<", 10);
            addImplicitResolver(Tag.NULL, NULL, "~nN\0", 10);
            addImplicitResolver(Tag.NULL, EMPTY, null, 10);
        }
    }
}
