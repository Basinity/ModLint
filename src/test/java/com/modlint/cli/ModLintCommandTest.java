package com.modlint.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modlint.testutil.FixtureJars;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ModLintCommandTest {

    private record Run(int exitCode, String stdout) {
    }

    private Run run(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new ModLintCommand()).execute(args);
            return new Run(exitCode, out.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(original);
        }
    }

    private static void packMissingDependencyFolder(Path dir) throws IOException {
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"),
                dir.resolve("iris-1.7.6+mc1.20.1.jar"));
    }

    @Test
    void reportsFindingsAndExitsOne(@TempDir Path dir) throws IOException {
        packMissingDependencyFolder(dir);

        Run run = run(dir.toString());

        assertEquals(1, run.exitCode());
        assertTrue(run.stdout().contains("HIGH (1)"));
        assertTrue(run.stdout().contains("missing-dependency"));
        assertTrue(run.stdout().contains("Fix: Install sodium"));
    }

    @Test
    void ignoreFileSuppressesTheFindingAndExitsZero(@TempDir Path dir) throws IOException {
        packMissingDependencyFolder(dir);
        Path ignore = dir.resolve("rules.txt");
        Files.writeString(ignore, "missing-dependency sodium\n");

        Run run = run(dir.toString(), "--ignore", ignore.toString());

        assertEquals(0, run.exitCode());
        assertTrue(run.stdout().contains("No findings (1 ignored)."));
    }

    @Test
    void defaultIgnoreFileNextToTheFolderIsPickedUp(@TempDir Path dir) throws IOException {
        packMissingDependencyFolder(dir);
        Files.writeString(dir.resolve(".modlintignore"), "missing-dependency\n");

        assertEquals(0, run(dir.toString()).exitCode());
    }

    @Test
    void jsonOutputIsParseableAndCarriesTheFindings(@TempDir Path dir) throws IOException {
        packMissingDependencyFolder(dir);

        Run run = run(dir.toString(), "--json", "--mc-version", "1.20.1");

        assertEquals(1, run.exitCode());
        JsonObject report = JsonParser.parseString(run.stdout()).getAsJsonObject();
        assertEquals(1, report.get("jars").getAsInt());
        assertEquals("1.20.1", report.get("minecraftVersion").getAsString());
        assertEquals("missing-dependency",
                report.getAsJsonArray("findings").get(0).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void cleanFolderExitsZero(@TempDir Path dir) {
        Run run = run(dir.toString());

        assertEquals(0, run.exitCode());
        assertTrue(run.stdout().contains("No findings."));
    }

    @Test
    void missingTargetExitsTwo(@TempDir Path dir) {
        assertEquals(2, run(dir.resolve("nope").toString()).exitCode());
    }

    @Test
    void corruptMrpackExitsTwo(@TempDir Path dir) throws IOException {
        Path mrpack = dir.resolve("corrupt.mrpack");
        Files.writeString(mrpack, "not a zip");

        assertEquals(2, run(mrpack.toString()).exitCode());
    }

    @Test
    void jarWithMalformedFabricMetadataExitsTwo(@TempDir Path dir) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(dir.resolve("broken.jar")))) {
            out.putNextEntry(new JarEntry("fabric.mod.json"));
            out.write("not json at all".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        assertEquals(2, run(dir.toString()).exitCode());
    }

    @Test
    void extraRulesFileFiresItsRule(@TempDir Path dir) throws IOException {
        packMissingDependencyFolder(dir); // provides iris 1.7.6
        Path rules = dir.resolve("rules.yaml");
        Files.writeString(rules, """
                rules:
                  - id: iris-test-rule
                    mods: { iris: "1.7.x" }
                    severity: low
                    problem: "Test rule matched iris."
                    fix: "Do the thing."
                """);

        Run run = run(dir.toString(), "--rules", rules.toString());

        assertEquals(1, run.exitCode());
        assertTrue(run.stdout().contains("known-bad-combination"));
        assertTrue(run.stdout().contains("iris-test-rule"));
        assertTrue(run.stdout().contains("Fix: Do the thing."));
    }

    @Test
    void mrpackIsExtractedAndItsMinecraftVersionIsUsed(@TempDir Path dir) throws IOException {
        // An offline pack: no remote files, one override jar (iris pins minecraft 1.20.1),
        // and an index declaring Minecraft 1.21.1, which must trigger the range check.
        Path mrpack = dir.resolve("test.mrpack");
        Path irisJar = dir.resolve("iris-tmp.jar");
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"), irisJar);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(mrpack))) {
            out.putNextEntry(new JarEntry("modrinth.index.json"));
            out.write("""
                {
                  "formatVersion": 1,
                  "game": "minecraft",
                  "name": "test pack",
                  "versionId": "1",
                  "dependencies": { "minecraft": "1.21.1" },
                  "files": []
                }
                """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("overrides/mods/iris-1.7.6+mc1.20.1.jar"));
            out.write(Files.readAllBytes(irisJar));
            out.closeEntry();
        }

        Run run = run(mrpack.toString(), "--json");

        assertEquals(1, run.exitCode());
        JsonObject report = JsonParser.parseString(run.stdout()).getAsJsonObject();
        assertEquals("1.21.1", report.get("minecraftVersion").getAsString());
        assertEquals(1, report.get("jars").getAsInt());
        assertTrue(report.getAsJsonArray("findings").toString().contains("minecraft"));
    }
}
