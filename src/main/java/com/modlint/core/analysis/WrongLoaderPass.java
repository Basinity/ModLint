package com.modlint.core.analysis;

import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds jars whose metadata targets another loader and not Fabric. Stays quiet for
 * Forge/NeoForge jars when a cross-loader compatibility layer (Kilt, Sinytra Connector)
 * is installed, because such packs run those jars on purpose. When the foreign jars
 * outnumber the Fabric mods, the folder itself is a Forge/NeoForge pack: that becomes
 * one finding about the folder instead of one per jar.
 */
public final class WrongLoaderPass implements AnalysisPass {

    @Override
    public List<Finding> analyze(ModSet mods) {
        if (mods.foreignDominant()) {
            long foreign = mods.foreignJarCount();
            long fabric = mods.jars().stream().filter(jar -> jar.fabricMod().isPresent()).count();
            return List.of(new Finding("not-a-fabric-pack", Severity.MEDIUM,
                    List.of(),
                    foreign + " of the " + mods.jars().size() + " jars target another loader and only "
                            + fabric + " are Fabric mods, so this folder belongs to a Forge/NeoForge pack. "
                            + "ModLint analyzes Fabric packs; dependency checks were skipped because that "
                            + "pack's own loader resolves them.",
                    "Point ModLint at a Fabric mods folder. Forge/NeoForge support is a planned later phase."));
        }
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
