package com.modlint.core.analysis;

import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the same library bundled as a nested jar-in-jar by several mods at different major
 * versions. Fabric loads only the newest copy, so a bundler expecting the older major may
 * hit a breaking API change.
 */
public final class BundledLibraryClashPass implements AnalysisPass {

    private record Bundled(String bundlerId, String version) {
    }

    @Override
    public List<Finding> analyze(ModSet mods) {
        Map<String, List<Bundled>> bundlersByLibrary = new LinkedHashMap<>();
        for (ScannedJar jar : mods.jars()) {
            jar.fabricMod().ifPresent(mod -> {
                for (var nested : jar.nestedMods()) {
                    bundlersByLibrary.computeIfAbsent(nested.id(), key -> new ArrayList<>())
                            .add(new Bundled(mod.id(), nested.version()));
                }
            });
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<Bundled>> entry : bundlersByLibrary.entrySet()) {
            List<Bundled> bundlers = entry.getValue();
            long majors = bundlers.stream().map(bundled -> major(bundled.version()))
                    .filter(major -> major != null).distinct().count();
            if (majors < 2) {
                continue;
            }
            String detail = bundlers.stream()
                    .map(bundled -> bundled.bundlerId() + " bundles " + bundled.version())
                    .sorted().reduce((a, b) -> a + ", " + b).orElseThrow();
            findings.add(new Finding("bundled-library-clash", Severity.MEDIUM,
                    bundlers.stream().map(Bundled::bundlerId).distinct().toList(),
                    "Different major versions of the nested library '" + entry.getKey() + "' are bundled: "
                            + detail + ". Only the newest loads, which may break the mod expecting the older one.",
                    "Update the mods to versions bundling compatible releases of " + entry.getKey() + "."));
        }
        return findings;
    }

    private static String major(String version) {
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        return end == 0 ? null : version.substring(0, end);
    }
}
