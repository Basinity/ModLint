package com.modlint.core.model;

/** A mod loader a jar can carry metadata for. A jar may target several at once. */
public enum ModLoader {
    FABRIC("Fabric"),
    QUILT("Quilt"),
    FORGE("Forge"),
    NEOFORGE("NeoForge");

    private final String displayName;

    ModLoader(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** True for Forge and NeoForge, whose metadata and version-range syntax are shared. */
    public boolean isForgeFamily() {
        return this == FORGE || this == NEOFORGE;
    }
}
