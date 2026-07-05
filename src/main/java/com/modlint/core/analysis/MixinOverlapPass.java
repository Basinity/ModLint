package com.modlint.core.analysis;

import com.modlint.core.model.MixinInjection;
import com.modlint.core.model.ScannedJar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds two mods placing intrusive Mixin injections on the same target class and method,
 * where coexistence is fragile. A flagged overlap is a risk, not a certainty, so findings
 * are POTENTIAL: verify the pair in game, then suppress the finding if they coexist fine.
 */
public final class MixinOverlapPass implements AnalysisPass {

    private record Injector(String modId, MixinInjection injection) {
    }

    @Override
    public List<Finding> analyze(ModSet mods) {
        Map<String, List<Injector>> byTarget = new LinkedHashMap<>();
        for (ScannedJar jar : mods.jars()) {
            jar.fabricMod().ifPresent(mod -> {
                for (MixinInjection injection : jar.contents().mixinInjections()) {
                    byTarget.computeIfAbsent(injection.targetClass() + "#" + injection.targetMethod(),
                            key -> new ArrayList<>()).add(new Injector(mod.id(), injection));
                }
            });
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<Injector>> entry : byTarget.entrySet()) {
            List<String> modIds = entry.getValue().stream().map(Injector::modId).distinct().toList();
            if (modIds.size() < 2) {
                continue;
            }
            String injectors = entry.getValue().stream()
                    .map(injector -> injector.modId() + " " + injector.injection().injector())
                    .distinct().sorted().reduce((a, b) -> a + ", " + b).orElseThrow();
            findings.add(new Finding("mixin-overlap", Severity.POTENTIAL,
                    modIds,
                    "Intrusive Mixin injections from several mods hit " + entry.getKey()
                            + ": " + injectors + ". Whichever applies last wins, and the loser may misbehave.",
                    "Test this pair in game; if it works, suppress this finding via the ignore file."));
        }
        return findings;
    }
}
