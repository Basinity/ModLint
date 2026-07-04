package com.modlint.core.analysis;

import java.util.List;

/**
 * One detected problem: a stable type id (e.g. {@code missing-dependency}), the severity,
 * the mods involved (mod ids, or file names for jars without parsed metadata), a plain
 * explanation, and a suggested fix.
 */
public record Finding(String type, Severity severity, List<String> mods, String problem, String fix) {
}
