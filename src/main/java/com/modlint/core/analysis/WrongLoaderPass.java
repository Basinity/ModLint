package com.modlint.core.analysis;

import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds jars whose metadata targets another loader and not Fabric. Stays quiet for
 * Forge/NeoForge jars when a cross-loader compatibility layer (Kilt, Sinytra Connector)
 * is installed, because such packs run those jars on purpose.
 */
public final class WrongLoaderPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        boolean compatLayer = mods.hasCompatLayer();
        List<Finding> findings = new ArrayList<>();
        for (ScannedJar jar : mods.jars()) {
            if (jar.loaders().isEmpty() || jar.loaders().contains(ModLoader.FABRIC)) {
                continue;
            }
            boolean forgeFamily = jar.loaders().contains(ModLoader.FORGE)
                    || jar.loaders().contains(ModLoader.NEOFORGE);
            if (compatLayer && forgeFamily) {
                continue;
            }
            String file = jar.path().getFileName().toString();
            findings.add(new Finding("wrong-loader", Severity.HIGH,
                    List.of(file),
                    file + " carries " + jar.loaders() + " metadata and none for Fabric, so Fabric ignores it.",
                    "Replace it with the Fabric build of the mod, or remove it."));
        }
        return findings;
    }
}
