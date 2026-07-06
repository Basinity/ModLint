package com.modlint.core.rules;

import com.modlint.core.analysis.Severity;
import java.util.List;
import java.util.Map;

/**
 * One masterlist rule: a known-bad combination that fires when every mod in {@code mods} is
 * present in a version matching its range, and none of the {@code absent} ids is installed.
 * The absent list expresses missing-companion rules ("X with Y but without Z breaks").
 * Ranges use the same fabric.mod.json predicate syntax as everywhere else; {@code "*"}
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
