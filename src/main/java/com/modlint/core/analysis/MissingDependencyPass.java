package com.modlint.core.analysis;

import com.modlint.core.model.ModInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Finds declared {@code depends} ids that nothing in the set provides. */
public final class MissingDependencyPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        if (mods.foreignDominant()) {
            return List.of(); // A Forge-pack folder: its compat layer satisfies deps this scan can't see.
        }
        List<Finding> findings = new ArrayList<>();
        for (ModInfo mod : mods.topLevelMods()) {
            for (Map.Entry<String, List<String>> dependency : mod.depends().entrySet()) {
                String depId = dependency.getKey();
                if (ModSet.PLATFORM_IDS.contains(depId) || ModSet.LOADER_BUNDLED_IDS.contains(depId)
                        || mods.providerOf(depId).isPresent()) {
                    continue;
                }
                findings.add(new Finding("missing-dependency", Severity.HIGH,
                        List.of(mod.id(), depId),
                        mod.name() + " requires " + depId + " " + dependency.getValue()
                                + ", but no installed mod provides it.",
                        "Install " + depId + " in a version matching " + dependency.getValue() + "."));
            }
        }
        return findings;
    }
}
