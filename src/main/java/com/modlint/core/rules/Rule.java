package com.modlint.core.rules;

import com.modlint.core.analysis.Severity;
import java.util.List;
import java.util.Map;

/**
 * One masterlist rule: a known-bad combination that fires when every listed mod is present
 * in a version matching its range. Ranges use the same fabric.mod.json predicate syntax as
 * everywhere else; {@code "*"} means any version.
 */
public record Rule(
        String id,
        Map<String, List<String>> mods,
        Severity severity,
        String problem,
        String fix) {
}
