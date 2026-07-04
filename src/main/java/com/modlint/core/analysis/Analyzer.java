package com.modlint.core.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Runs every analysis pass over a mod set and returns the findings, most severe first. */
public final class Analyzer {

    private final List<AnalysisPass> passes = List.of(
            new MissingDependencyPass(),
            new VersionRangeViolationPass(),
            new DuplicateModIdPass(),
            new DeclaredIncompatibilityPass(),
            new WrongLoaderPass());

    public List<Finding> analyze(ModSet mods) {
        List<Finding> findings = new ArrayList<>();
        for (AnalysisPass pass : passes) {
            findings.addAll(pass.analyze(mods));
        }
        findings.sort(Comparator.comparing(Finding::severity));
        return List.copyOf(findings);
    }
}
