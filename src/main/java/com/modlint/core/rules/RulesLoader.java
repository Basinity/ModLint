package com.modlint.core.rules;

import com.modlint.core.analysis.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads masterlist rules from YAML. The document is a {@code rules:} list; each entry has
 * {@code id}, a {@code mods} map of id to version range (string or list), {@code severity},
 * {@code problem}, and {@code fix}.
 */
public final class RulesLoader {

    private static final String BUNDLED_MASTERLIST = "/modlint/masterlist.yaml";

    /** The masterlist shipped inside the tool. */
    public static List<Rule> loadBundled() {
        try (InputStream in = RulesLoader.class.getResourceAsStream(BUNDLED_MASTERLIST)) {
            if (in == null) {
                throw new IllegalStateException("Bundled masterlist missing from classpath");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Bundled masterlist unreadable", e);
        }
    }

    public static List<Rule> load(Path rulesFile) throws IOException {
        return parse(Files.readString(rulesFile));
    }

    /** @throws IllegalArgumentException if the document or a rule is malformed */
    @SuppressWarnings("unchecked")
    public static List<Rule> parse(String yamlText) {
        Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
        if (!(document instanceof Map<?, ?> root) || !(root.get("rules") instanceof List<?> ruleList)) {
            throw new IllegalArgumentException("Masterlist must be a document with a 'rules' list");
        }
        List<Rule> rules = new ArrayList<>();
        for (Object entry : ruleList) {
            rules.add(parseRule((Map<String, Object>) entry));
        }
        return List.copyOf(rules);
    }

    private static Rule parseRule(Map<String, Object> entry) {
        String id = requireString(entry, "id");
        Object modsValue = entry.get("mods");
        if (!(modsValue instanceof Map<?, ?> modsMap) || modsMap.size() < 1) {
            throw new IllegalArgumentException("Rule '" + id + "' needs a non-empty 'mods' map");
        }
        Map<String, List<String>> mods = new LinkedHashMap<>();
        for (Map.Entry<?, ?> condition : modsMap.entrySet()) {
            mods.put(condition.getKey().toString(), ranges(id, condition.getValue()));
        }
        Severity severity;
        try {
            severity = Severity.valueOf(requireString(entry, "severity").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rule '" + id + "' has an unknown severity");
        }
        return new Rule(id, Map.copyOf(mods), severity, requireString(entry, "problem"), requireString(entry, "fix"));
    }

    private static List<String> ranges(String ruleId, Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        if (value != null) {
            return List.of(value.toString());
        }
        throw new IllegalArgumentException("Rule '" + ruleId + "' has a mod without a version range");
    }

    private static String requireString(Map<String, Object> entry, String field) {
        Object value = entry.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("A rule is missing required field '" + field + "'");
        }
        return value.toString();
    }

    private RulesLoader() {
    }
}
