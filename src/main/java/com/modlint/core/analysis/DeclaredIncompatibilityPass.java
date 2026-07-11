package com.modlint.core.analysis;

import com.modlint.core.model.ModInfo;
import com.modlint.core.version.VersionRanges;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds pairs where one mod's declared {@code breaks} (hard, HIGH) or {@code conflicts}
 * (soft, MEDIUM) names another installed mod in a matching version.
 */
public final class DeclaredIncompatibilityPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        List<Finding> findings = new ArrayList<>();
        for (ModInfo mod : mods.topLevelMods()) {
            collect(mods, mod, mod.breaks(), "breaks", Severity.HIGH, findings);
            collect(mods, mod, mod.conflicts(), "conflicts with", Severity.MEDIUM, findings);
        }
        return findings;
    }

    private static void collect(ModSet mods, ModInfo mod, Map<String, List<String>> relation,
                                String verb, Severity severity, List<Finding> findings) {
        for (Map.Entry<String, List<String>> entry : relation.entrySet()) {
            String targetId = entry.getKey();
            if (ModSet.LOADER_BUNDLED_IDS.contains(targetId)) {
                continue; // The loader's own copy wins, and its version isn't in the folder.
            }
            List<String> ranges = entry.getValue();
            ModSet.Provider provider = mods.providerOf(targetId).orElse(null);
            if (provider == null || provider.modId().equals(mod.id())
                    || !VersionRanges.satisfies(mod.loader(), provider.version(), ranges)) {
                continue;
            }
            findings.add(new Finding("declared-incompatibility", severity,
                    List.of(mod.id(), targetId),
                    mod.name() + " declares it " + verb + " " + targetId
                            + (ranges.isEmpty() ? " in any version" : " " + ranges)
                            + ", and the installed version " + provider.version() + " matches.",
                    ranges.isEmpty() ? "Remove one of the two mods."
                            : "Update " + targetId + " out of the declared range, or remove one of the two mods."));
        }
    }
}
