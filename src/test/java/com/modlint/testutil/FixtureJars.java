package com.modlint.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/** Packs the fixture directories under {@code src/test/resources/fixtures} into mod jars. */
public final class FixtureJars {

    private FixtureJars() {
    }

    /** Resolves a fixture mod directory, e.g. {@code fixture("wrong-loader", "jei-1.20.1-forge-15.20.0.130")}. */
    public static Path fixture(String conflictCase, String modDir) {
        try {
            Path fixtures = Path.of(FixtureJars.class.getClassLoader().getResource("fixtures").toURI());
            return fixtures.resolve(conflictCase).resolve(modDir);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("fixtures directory not on the test classpath", e);
        }
    }

    /** Packs a fixture mod directory into a jar at {@code targetJar}, one entry per file. */
    public static void packJar(Path modDir, Path targetJar) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(targetJar));
             Stream<Path> files = Files.walk(modDir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(modDir.relativize(file).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
