package com.modlint.core.analysis;

import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds two mods shipping the same {@code assets/} or {@code data/} file, where whichever
 * loads last silently wins. Paths the game merges (language files, tags) are excluded at
 * scan time. One finding per group of mods sharing paths, with example paths.
 */
public final class ResourceOverridePass implements AnalysisPass {

    private static final int EXAMPLE_PATHS = 3;

    @Override
    public List<Finding> analyze(ModSet mods) {
        Map<String, List<String>> modsByPath = new LinkedHashMap<>();
        for (ScannedJar jar : mods.jars()) {
            mods.primaryMod(jar).ifPresent(mod -> {
                for (String path : jar.contents().resourcePaths()) {
                    modsByPath.computeIfAbsent(path, key -> new ArrayList<>()).add(mod.id());
                }
            });
        }
        Map<List<String>, List<String>> pathsByModPair = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : modsByPath.entrySet()) {
            List<String> owners = entry.getValue().stream().distinct().sorted().toList();
            if (owners.size() > 1) {
                pathsByModPair.computeIfAbsent(owners, key -> new ArrayList<>()).add(entry.getKey());
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<List<String>, List<String>> entry : pathsByModPair.entrySet()) {
            List<String> paths = entry.getValue();
            String examples = String.join(", ", paths.subList(0, Math.min(EXAMPLE_PATHS, paths.size())))
                    + (paths.size() > EXAMPLE_PATHS ? ", and " + (paths.size() - EXAMPLE_PATHS) + " more" : "");
            findings.add(new Finding("resource-override", Severity.MEDIUM,
                    entry.getKey(),
                    String.join(", ", entry.getKey()) + " ship " + paths.size()
                            + (paths.size() == 1 ? " identical resource file" : " identical resource files")
                            + " (" + examples + "); whichever loads last silently wins.",
                    "Check the files match intent; if the override is deliberate, suppress this finding."));
        }
        return findings;
    }
}
