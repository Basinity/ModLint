package com.modlint.core.model;

/**
 * The parsed identity of a single mod, normalized from its loader-specific metadata.
 * Kept loader-agnostic so the later analysis passes do not depend on Fabric specifics.
 */
public record ModInfo(String id, String version, String name) {
}
