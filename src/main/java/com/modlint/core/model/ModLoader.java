package com.modlint.core.model;

/** A mod loader a jar can carry metadata for. A jar may target several at once. */
public enum ModLoader {
    FABRIC,
    QUILT,
    FORGE,
    NEOFORGE
}
