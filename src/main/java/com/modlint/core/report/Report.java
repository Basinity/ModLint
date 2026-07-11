package com.modlint.core.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.modlint.core.analysis.Finding;
import com.modlint.core.analysis.ModSet;
import com.modlint.core.model.ModLoader;
import java.util.List;
import java.util.Locale;

/**
 * The analysis result the engine hands to its front-ends: what was scanned plus the findings,
 * most severe first. {@code loader} is the target loader the set was analyzed for, lowercase;
 * {@code mods} counts the top-level mods that loader would load. {@link #toJson()} is the
 * stable machine-readable form; field names and order are part of the contract.
 */
public record Report(int jars, int mods, String loader, String minecraftVersion, List<Finding> findings) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Report of(ModSet mods, List<Finding> findings) {
        return new Report(mods.jars().size(), mods.topLevelMods().size(),
                mods.targetLoader().name().toLowerCase(Locale.ROOT),
                mods.minecraftVersion().orElse(null), findings);
    }

    /** The target loader's display name ("Fabric", "NeoForge"), for human-readable output. */
    public String loaderDisplayName() {
        return ModLoader.valueOf(loader.toUpperCase(Locale.ROOT)).displayName();
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
