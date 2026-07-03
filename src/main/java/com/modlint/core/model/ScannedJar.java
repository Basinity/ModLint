package com.modlint.core.model;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * One jar found in a mods folder: which loaders its metadata targets, and the parsed
 * Fabric metadata when it has any. An empty loader set means the jar carries no known
 * mod metadata at all.
 */
public record ScannedJar(Path path, Set<ModLoader> loaders, Optional<ModInfo> fabricMod) {
}
