package com.modlint.core.analysis;

import com.modlint.core.model.ModInfo;
import com.modlint.core.version.VersionRanges;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds declared {@code depends} ids that are present, but only in versions outside the
 * declared range. Minecraft itself is checked when the set was built with a game version.
 */
public final class VersionRangeViolationPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        List<Finding> findings = new ArrayList<>();
        for (ModInfo mod : mods.topLevelMods()) {
            for (Map.Entry<String, List<String>> dependency : mod.depends().entrySet()) {
                String depId = dependency.getKey();
                List<ModSet.Provider> providers = mods.providersOf(depId);
                if (providers.isEmpty()) {
                    continue; // Absent entirely: the missing-dependency pass owns that.
                }
                List<String> ranges = dependency.getValue();
                if (providers.stream().anyMatch(provider -> VersionRanges.satisfies(provider.version(), ranges))) {
                    continue;
                }
                String found = providers.stream().map(ModSet.Provider::version).distinct()
                        .reduce((a, b) -> a + ", " + b).orElseThrow();
                findings.add(new Finding("version-range-violation", Severity.HIGH,
                        List.of(mod.id(), depId),
                        mod.name() + " requires " + depId + " " + ranges + ", but the installed version is "
                                + found + ".",
                        "Install a version of " + depId + " matching " + ranges
                                + ", or a build of " + mod.id() + " that accepts " + found + "."));
            }
        }
        return findings;
    }
}
