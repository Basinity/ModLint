package com.modlint.core.analysis;

import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds two top-level jars providing the same mod id. Nested duplicates are normal and ignored. */
public final class DuplicateModIdPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        Map<String, List<ScannedJar>> byId = new LinkedHashMap<>();
        for (ScannedJar jar : mods.jars()) {
            for (var mod : mods.nativeMods(jar)) {
                byId.computeIfAbsent(mod.id(), key -> new ArrayList<>()).add(jar);
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<ScannedJar>> entry : byId.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            List<String> files = entry.getValue().stream()
                    .map(jar -> jar.path().getFileName().toString()).toList();
            findings.add(new Finding("duplicate-mod-id", Severity.HIGH,
                    files,
                    files.size() + " jars provide the same mod id '" + entry.getKey() + "': "
                            + String.join(", ", files) + ".",
                    "Remove all but one of the jars."));
        }
        return findings;
    }
}
