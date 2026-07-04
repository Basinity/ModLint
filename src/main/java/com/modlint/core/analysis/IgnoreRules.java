package com.modlint.core.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Suppression rules from an ignore file. Each non-blank, non-{@code #} line is either a
 * finding type ({@code wrong-loader}), suppressing every finding of that type, or a type
 * followed by a mod ({@code missing-dependency sodium}), suppressing findings of that type
 * involving that mod id or file name.
 */
public final class IgnoreRules {

    private record Rule(String type, String mod) {

        boolean matches(Finding finding) {
            return finding.type().equals(type) && (mod == null || finding.mods().contains(mod));
        }
    }

    private final List<Rule> rules;

    private IgnoreRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static IgnoreRules none() {
        return new IgnoreRules(List.of());
    }

    public static IgnoreRules parse(List<String> lines) {
        List<Rule> rules = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            rules.add(new Rule(parts[0], parts.length == 2 ? parts[1] : null));
        }
        return new IgnoreRules(List.copyOf(rules));
    }

    public boolean ignores(Finding finding) {
        return rules.stream().anyMatch(rule -> rule.matches(finding));
    }
}
