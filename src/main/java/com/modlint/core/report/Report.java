package com.modlint.core.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.modlint.core.analysis.Finding;
import com.modlint.core.model.ScannedJar;
import java.util.List;
import java.util.Optional;

/**
 * The analysis result the engine hands to its front-ends: what was scanned plus the findings,
 * most severe first. {@link #toJson()} is the stable machine-readable form; field names and
 * order are part of the contract.
 */
public record Report(int jars, int fabricMods, String minecraftVersion, List<Finding> findings) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Report of(List<ScannedJar> jars, Optional<String> minecraftVersion, List<Finding> findings) {
        int fabricMods = (int) jars.stream().filter(jar -> jar.fabricMod().isPresent()).count();
        return new Report(jars.size(), fabricMods, minecraftVersion.orElse(null), findings);
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
