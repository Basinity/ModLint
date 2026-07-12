package com.modlint.cli;

import com.modlint.core.analysis.Analyzer;
import com.modlint.core.analysis.Finding;
import com.modlint.core.analysis.IgnoreRules;
import com.modlint.core.analysis.ModSet;
import com.modlint.core.analysis.Severity;
import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import com.modlint.core.report.Report;
import com.modlint.core.rules.Rule;
import com.modlint.core.rules.RulesLoader;
import com.modlint.core.scan.ModsFolderScanner;
import com.modlint.core.scan.MrpackInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** The modlint CLI: scans a mods folder or a Modrinth pack and reports conflict findings. */
@Command(name = "modlint", mixinStandardHelpOptions = true, version = "modlint 0.1.0",
        description = "Statically analyzes a Fabric/Forge/NeoForge mods folder or Modrinth .mrpack for conflicts.",
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {"0:no findings", "1:findings reported", "2:usage or input error"})
public final class ModLintCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<target>",
            description = "A mods folder, or a Modrinth .mrpack file.")
    private Path target;

    @Option(names = "--mc-version", paramLabel = "<version>",
            description = "Minecraft version to check 'minecraft' dependency ranges against. "
                    + "For an .mrpack, defaults to the version declared in the pack.")
    private String mcVersion;

    @Option(names = "--loader", paramLabel = "<loader>",
            description = "Loader to analyze for: fabric, forge or neoforge. Defaults to the loader "
                    + "most jars target (for an .mrpack, the loader declared in the pack).")
    private ModLoader loader;

    @Option(names = "--json", description = "Emit the report as JSON instead of text.")
    private boolean json;

    @Option(names = "--ignore", paramLabel = "<file>",
            description = "Ignore file suppressing findings, one rule per line: a finding type, "
                    + "or a type followed by a mod id / file name. "
                    + "Defaults to .modlintignore next to the target, when present.")
    private Path ignoreFile;

    @Option(names = "--rules", paramLabel = "<file>",
            description = "Extra masterlist YAML of known-bad combinations, added to the bundled one.")
    private Path rulesFile;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ModLintCommand())
                .setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }

    @Override
    public Integer call() {
        if (loader == ModLoader.QUILT) {
            System.err.println("Quilt is not an analyzable target; use fabric, forge or neoforge.");
            return 2;
        }
        Path workDir = null;
        try {
            Path modsFolder = target;
            Optional<String> minecraftVersion = Optional.ofNullable(mcVersion);
            Optional<ModLoader> targetLoader = Optional.ofNullable(loader);
            if (Files.isRegularFile(target) && target.getFileName().toString().endsWith(".mrpack")) {
                workDir = Files.createTempDirectory("modlint-");
                MrpackInput.Materialized pack = new MrpackInput().materialize(target, workDir);
                modsFolder = pack.modsFolder();
                minecraftVersion = minecraftVersion.or(pack::minecraftVersion);
                targetLoader = targetLoader.or(pack::loader);
            } else if (!Files.isDirectory(target)) {
                System.err.println(target + " is neither a mods folder nor an .mrpack file.");
                return 2;
            }

            List<Rule> rules = new ArrayList<>(RulesLoader.loadBundled());
            if (rulesFile != null) {
                rules.addAll(RulesLoader.load(rulesFile));
            }
            List<ScannedJar> jars = new ModsFolderScanner().scan(modsFolder);
            Optional<String> mc = minecraftVersion;
            ModSet modSet = targetLoader.map(t -> new ModSet(jars, mc, t))
                    .orElseGet(() -> new ModSet(jars, mc));
            List<Finding> findings = new Analyzer(rules).analyze(modSet);
            IgnoreRules ignore = loadIgnoreRules();
            List<Finding> reported = findings.stream().filter(finding -> !ignore.ignores(finding)).toList();
            Report report = Report.of(modSet, reported);

            if (json) {
                System.out.println(report.toJson());
            } else {
                printText(report, findings.size() - reported.size());
            }
            return reported.isEmpty() ? 0 : 1;
        } catch (IOException | IllegalArgumentException e) {
            // Unreadable input (a corrupt jar or .mrpack, a malformed rules file) is the
            // documented exit code 2, not a stack trace colliding with "findings reported".
            System.err.println("Error: " + (e.getMessage() == null ? e : e.getMessage()));
            return 2;
        } finally {
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
    }

    /** Best-effort removal of the .mrpack work folder; leftovers would pile up in the OS temp dir. */
    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            // Nothing to do; the OS temp folder is the fallback.
        }
    }

    private IgnoreRules loadIgnoreRules() throws IOException {
        Path file = ignoreFile;
        if (file == null) {
            Path fallback = (Files.isDirectory(target) ? target : target.toAbsolutePath().getParent())
                    .resolve(".modlintignore");
            if (!Files.isRegularFile(fallback)) {
                return IgnoreRules.none();
            }
            file = fallback;
        }
        return IgnoreRules.parse(Files.readAllLines(file));
    }

    private static void printText(Report report, int ignoredCount) {
        System.out.printf("Scanned %d jars (%d %s mods)%s.%n", report.jars(), report.mods(),
                report.loaderDisplayName(),
                report.minecraftVersion() == null ? "" : " for Minecraft " + report.minecraftVersion());
        for (Severity severity : Severity.values()) {
            List<Finding> group = report.findings().stream()
                    .filter(finding -> finding.severity() == severity).toList();
            if (group.isEmpty()) {
                continue;
            }
            System.out.printf("%n%s (%d)%n", severity, group.size());
            for (Finding finding : group) {
                System.out.printf("  [%s] %s%n    %s%n    Fix: %s%n",
                        finding.type(), String.join(", ", finding.mods()), finding.problem(), finding.fix());
            }
        }
        System.out.println();
        if (report.findings().isEmpty()) {
            System.out.println(ignoredCount == 0 ? "No findings."
                    : "No findings (" + ignoredCount + " ignored).");
        } else {
            System.out.printf("%d %s%s.%n", report.findings().size(),
                    report.findings().size() == 1 ? "finding" : "findings",
                    ignoredCount == 0 ? "" : " (" + ignoredCount + " ignored)");
        }
    }
}
