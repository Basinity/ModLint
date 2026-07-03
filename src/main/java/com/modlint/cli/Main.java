package com.modlint.cli;

import com.modlint.core.model.ScannedJar;
import com.modlint.core.scan.ModsFolderScanner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Dumps the mod list of a mods folder. A development front-end until the real
 * Picocli CLI replaces it.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: modlint <mods-folder>");
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
    }
}
