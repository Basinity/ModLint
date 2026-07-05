package com.modlint.core.analysis;

import com.modlint.core.model.AccessWidenerEntry;
import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the same member widened by several mods with genuinely divergent access keyword
 * sets. Widenings are additive, so one mod asking for a subset of another's keywords is
 * harmless and skipped; only members where neither mod's set contains the other's are
 * flagged, and even those rarely break outright.
 */
public final class AccessWidenerConflictPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        Map<String, Map<String, Set<String>>> accessesByModByMember = new LinkedHashMap<>();
        for (ScannedJar jar : mods.jars()) {
            jar.fabricMod().ifPresent(mod -> {
                for (AccessWidenerEntry entry : jar.contents().accessWideners()) {
                    accessesByModByMember
                            .computeIfAbsent(entry.member(), key -> new LinkedHashMap<>())
                            .computeIfAbsent(mod.id(), key -> new LinkedHashSet<>())
                            .add(entry.access());
                }
            });
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> entry : accessesByModByMember.entrySet()) {
            Map<String, Set<String>> byMod = entry.getValue();
            if (byMod.size() < 2 || !hasDivergentPair(List.copyOf(byMod.values()))) {
                continue;
            }
            String detail = byMod.entrySet().stream()
                    .map(mod -> mod.getKey() + " widens it as " + String.join("+", mod.getValue()))
                    .sorted().reduce((a, b) -> a + ", " + b).orElseThrow();
            findings.add(new Finding("access-widener-conflict", Severity.LOW,
                    List.copyOf(byMod.keySet()),
                    "Several mods widen '" + entry.getKey() + "' divergently: " + detail + ".",
                    "Usually harmless (widenings are additive), but verify both mods behave; then suppress."));
        }
        return findings;
    }

    /** True when some two sets diverge: neither contains the other. */
    private static boolean hasDivergentPair(List<Set<String>> sets) {
        for (int i = 0; i < sets.size(); i++) {
            for (int j = i + 1; j < sets.size(); j++) {
                if (!sets.get(i).containsAll(sets.get(j)) && !sets.get(j).containsAll(sets.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
