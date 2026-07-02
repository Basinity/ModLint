package com.modlint.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.modlint.core.model.ModInfo;
import com.modlint.testutil.FixtureJars;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the real-world conflict fixtures: every Fabric fixture jar must keep parsing into the
 * expected {@link ModInfo}, and the wrong-loader jar must keep failing as a non-Fabric jar.
 * The conflicts they seed get their own tests with the analysis passes that detect them.
 */
class ConflictFixturesTest {

    private final JarModReader reader = new JarModReader();
    private final FabricModMetadataParser parser = new FabricModMetadataParser();

    static Stream<Arguments> fabricFixtures() {
        return Stream.of(
            Arguments.of("missing-dependency", "amendments-1.20-2.2.5-fabric", "amendments", "1.20-2.2.5"),
            Arguments.of("missing-dependency", "fabric-api-0.92.9+1.20.1", "fabric-api", "0.92.9+1.20.1"),
            Arguments.of("version-range-violation", "iris-1.7.6+mc1.20.1", "iris", "1.7.6+mc1.20.1"),
            Arguments.of("version-range-violation", "sodium-fabric-0.6.13+mc1.21.1", "sodium", "0.6.13+mc1.21.1"),
            Arguments.of("declared-breaks", "sodium-fabric-0.8.12+mc1.21.11", "sodium", "0.8.12+mc1.21.11"),
            Arguments.of("declared-breaks", "iris-fabric-1.10.4+mc1.21.11", "iris", "1.10.4+mc1.21.11"));
    }

    @ParameterizedTest
    @MethodSource("fabricFixtures")
    void fabricFixtureParses(String conflictCase, String modDir, String expectedId, String expectedVersion,
                             @TempDir Path dir) throws IOException {
        Path jar = dir.resolve(modDir + ".jar");
        FixtureJars.packJar(FixtureJars.fixture(conflictCase, modDir), jar);

        ModInfo mod = parser.parse(reader.readFabricMetadata(jar));

        assertEquals(expectedId, mod.id());
        assertEquals(expectedVersion, mod.version());
    }

    @ParameterizedTest
    @MethodSource("wrongLoaderFixture")
    void wrongLoaderFixtureIsNotAFabricMod(String conflictCase, String modDir, @TempDir Path dir) throws IOException {
        Path jar = dir.resolve(modDir + ".jar");
        FixtureJars.packJar(FixtureJars.fixture(conflictCase, modDir), jar);

        assertThrows(IOException.class, () -> reader.readFabricMetadata(jar));
    }

    static Stream<Arguments> wrongLoaderFixture() {
        return Stream.of(Arguments.of("wrong-loader", "corpse-forge-1.20.1-1.0.23"));
    }
}
