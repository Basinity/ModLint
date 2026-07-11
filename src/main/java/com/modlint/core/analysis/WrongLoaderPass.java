package com.modlint.core.analysis;

import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds jars whose metadata targets other loaders only, so the pack's own loader ignores
 * (or refuses) them. Stays quiet for jars of the opposite loader family when a cross-loader
 * compatibility layer (Kilt, Sinytra Connector) is installed, because such packs run those
 * jars on purpose.
 */
public final class WrongLoaderPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        ModLoader target = mods.targetLoader();
        boolean compatLayer = mods.hasCompatLayer();
        List<Finding> findings = new ArrayList<>();
        for (ScannedJar jar : mods.jars()) {
            if (jar.loaders().isEmpty()
                    || jar.loaders().stream().anyMatch(mods.acceptedLoaders()::contains)) {
                continue;
            }
            boolean oppositeFamily = jar.loaders().stream()
                    .anyMatch(loader -> loader.isForgeFamily() != target.isForgeFamily());
            if (compatLayer && oppositeFamily) {
                continue;
            }
            String file = jar.path().getFileName().toString();
            String carried = String.join("/", jar.loaders().stream().map(ModLoader::displayName).toList());
            findings.add(new Finding("wrong-loader", Severity.HIGH,
                    List.of(file),
                    file + " carries " + carried + " metadata and none for " + target.displayName()
                            + ", so " + target.displayName() + " ignores it.",
                    "Replace it with the " + target.displayName() + " build of the mod, or remove it."));
        }
        return findings;
    }
}
