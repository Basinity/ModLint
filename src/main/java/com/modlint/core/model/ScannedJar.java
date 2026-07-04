package com.modlint.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One jar found in a mods folder: which loaders its metadata targets, the parsed Fabric
 * metadata when it has any, and the Fabric mods bundled inside it as nested jar-in-jars
 * (flattened, all nesting levels). An empty loader set means the jar carries no known
 * mod metadata at all.
 */
public record ScannedJar(Path path, Set<ModLoader> loaders, Optional<ModInfo> fabricMod, List<ModInfo> nestedMods) {
}
