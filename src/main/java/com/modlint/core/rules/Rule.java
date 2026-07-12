package com.modlint.core.rules;

import com.modlint.core.analysis.Severity;
import java.util.List;
import java.util.Map;

/**
 * One masterlist rule: a known-bad combination that fires when every mod in {@code mods} is
 * present in a version matching its range, and none of the {@code absent} ids is installed.
 * The absent list expresses missing-companion rules ("X with Y but without Z breaks").
 * Ranges use fabric.mod.json predicate syntax regardless of the mod's loader; {@code "*"}
 * means any version.
 */
public record Rule(
        String id,
        Map<String, List<String>> mods,
        List<String> absent,
        Severity severity,
        String problem,
        String fix) {
}
