package com.modlint.core.model;

import java.util.List;

/**
 * What the deep passes need from inside a jar beyond its metadata: the intrusive Mixin
 * injections, the shipped {@code assets/} and {@code data/} file paths (minus paths the
 * game merges by design), and the access widener entries.
 */
public record JarContents(
        List<MixinInjection> mixinInjections,
        List<String> resourcePaths,
        List<AccessWidenerEntry> accessWideners) {

    public static JarContents empty() {
        return new JarContents(List.of(), List.of(), List.of());
    }
}
