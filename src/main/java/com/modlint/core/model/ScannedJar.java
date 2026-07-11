package com.modlint.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * One jar found in a mods folder: which loaders its metadata targets, the parsed mods it
 * declares (one per loader-specific metadata file it carries, several for a Forge file
 * declaring multiple mods), the mods bundled inside it as nested jar-in-jars (flattened,
 * all nesting levels), and the deep contents the analysis passes inspect. An empty loader
 * set means the jar carries no known mod metadata at all.
 */
public record ScannedJar(Path path, Set<ModLoader> loaders, List<ModInfo> mods,
                         List<ModInfo> nestedMods, JarContents contents) {
}
