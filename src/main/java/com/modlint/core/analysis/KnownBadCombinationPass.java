package com.modlint.core.analysis;

import com.modlint.core.rules.Rule;
import com.modlint.core.version.VersionRanges;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fires masterlist rules: a rule matches when every mod it lists is present in a version
 * inside the rule's range. The finding carries the rule's own severity, problem, and fix.
 */
public final class KnownBadCombinationPass implements AnalysisPass {

    private final List<Rule> rules;

    public KnownBadCombinationPass(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public List<Finding> analyze(ModSet mods) {
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : rules) {
            if (matches(rule, mods)) {
                findings.add(new Finding("known-bad-combination", rule.severity(),
                        List.copyOf(rule.mods().keySet()),
                        rule.problem() + " (masterlist rule '" + rule.id() + "')",
                        rule.fix()));
            }
        }
        return findings;
    }

    private static boolean matches(Rule rule, ModSet mods) {
        for (Map.Entry<String, List<String>> condition : rule.mods().entrySet()) {
            boolean satisfied = mods.providerOf(condition.getKey())
                    .filter(provider -> VersionRanges.satisfies(provider.version(), condition.getValue()))
                    .isPresent();
            if (!satisfied) {
                return false;
            }
        }
        for (String id : rule.absent()) {
            if (mods.providerOf(id).isPresent()) {
                return false;
            }
        }
        return true;
    }
}
