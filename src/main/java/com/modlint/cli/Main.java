package com.modlint.cli;

import com.modlint.core.analysis.Analyzer;
import com.modlint.core.analysis.Finding;
import com.modlint.core.analysis.ModSet;
import com.modlint.core.model.ScannedJar;
import com.modlint.core.scan.ModsFolderScanner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Scans a mods folder and prints the mod list and the findings. A development front-end
 * until the real Picocli CLI replaces it.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: modlint <mods-folder> [minecraft-version]");
            System.exit(2);
        }
        List<ScannedJar> jars = new ModsFolderScanner().scan(Path.of(args[0]));
        for (ScannedJar jar : jars) {
            System.out.println(jar.fabricMod()
                    .map(mod -> String.format("%-40s %-25s %s", mod.id(), mod.version(), mod.name()))
                    .orElseGet(() -> String.format("%-40s %-25s %s metadata, not a Fabric mod",
                            jar.path().getFileName(), "-", jar.loaders())));
        }
        long fabric = jars.stream().filter(jar -> jar.fabricMod().isPresent()).count();
        System.out.printf("%n%d jars: %d Fabric mods, %d other%n", jars.size(), fabric, jars.size() - fabric);

        Optional<String> minecraftVersion = args.length == 2 ? Optional.of(args[1]) : Optional.empty();
        List<Finding> findings = new Analyzer().analyze(new ModSet(jars, minecraftVersion));
        if (findings.isEmpty()) {
            System.out.println("No findings.");
            return;
        }
        System.out.printf("%d findings:%n", findings.size());
        for (Finding finding : findings) {
            System.out.printf("[%s] %s %s%n        %s%n        Fix: %s%n",
                    finding.severity(), finding.type(), finding.mods(), finding.problem(), finding.fix());
        }
    }
}
