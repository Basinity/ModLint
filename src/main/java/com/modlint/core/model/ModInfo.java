package com.modlint.core.model;

import java.util.List;
import java.util.Map;

/**
 * The parsed identity and declared relations of a single mod, normalized from its
 * loader-specific metadata. Kept loader-agnostic so the analysis passes do not depend
 * on Fabric specifics.
 *
 * <p>Relation maps go from a mod id to its declared version-range predicates, kept as
 * raw strings (e.g. {@code ">=1.20 <1.21"}, {@code "0.5.x"}); multiple predicates for
 * one id are alternatives (OR). Evaluating them is the dependency resolver's job.
 */
public record ModInfo(
        String id,
        String version,
        String name,
        List<String> provides,
        Map<String, List<String>> depends,
        Map<String, List<String>> breaks,
        Map<String, List<String>> conflicts) {
}
