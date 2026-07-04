package com.modlint.core.analysis;

import java.util.List;

/** One detection class: examines the mod set and emits the findings it is responsible for. */
public interface AnalysisPass {

    List<Finding> analyze(ModSet mods);
}
