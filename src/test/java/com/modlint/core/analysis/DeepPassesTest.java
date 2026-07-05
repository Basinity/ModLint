package com.modlint.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modlint.core.scan.ModsFolderScanner;
import com.modlint.testutil.DeepJars;
import com.modlint.testutil.SyntheticJars;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class DeepPassesTest {

    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    private ModSet scan(Path dir) throws IOException {
        return new ModSet(new ModsFolderScanner().scan(dir), Optional.empty());
    }

    @Test
    void overlappingIntrusiveMixinsAreFlaggedAsPotential(@TempDir Path dir) throws IOException {
        new DeepJars.ModJar("moda", "1.0.0")
                .withMixin("com.moda.mixin.RenderMixin", "net.minecraft.class_761", OVERWRITE, "method_3251", null)
                .write(dir.resolve("moda.jar"));
        new DeepJars.ModJar("modb", "1.0.0")
                .withMixin("com.modb.mixin.WorldMixin", "net.minecraft.class_761", REDIRECT,
                        "onRender", "method_3251(Lnet/minecraft/class_4184;)V")
                .write(dir.resolve("modb.jar"));

        List<Finding> findings = new MixinOverlapPass().analyze(scan(dir));

        assertEquals(1, findings.size());
        assertEquals("mixin-overlap", findings.get(0).type());
        assertEquals(Severity.POTENTIAL, findings.get(0).severity());
        assertEquals(List.of("moda", "modb"), findings.get(0).mods());
        assertTrue(findings.get(0).problem().contains("net.minecraft.class_761#method_3251"));
    }

    @Test
    void intrusiveMixinsOnDifferentMethodsDoNotOverlap(@TempDir Path dir) throws IOException {
        new DeepJars.ModJar("moda", "1.0.0")
                .withMixin("com.moda.mixin.RenderMixin", "net.minecraft.class_761", OVERWRITE, "method_1111", null)
                .write(dir.resolve("moda.jar"));
        new DeepJars.ModJar("modb", "1.0.0")
                .withMixin("com.modb.mixin.WorldMixin", "net.minecraft.class_761", OVERWRITE, "method_2222", null)
                .write(dir.resolve("modb.jar"));

        assertTrue(new MixinOverlapPass().analyze(scan(dir)).isEmpty());
    }

    @Test
    void identicalResourcePathsAreFlaggedOncePerModPair(@TempDir Path dir) throws IOException {
        new DeepJars.ModJar("moda", "1.0.0")
                .withEntry("assets/minecraft/textures/block/stone.png", "a")
                .withEntry("data/minecraft/recipes/boat.json", "a")
                .write(dir.resolve("moda.jar"));
        new DeepJars.ModJar("modb", "1.0.0")
                .withEntry("assets/minecraft/textures/block/stone.png", "b")
                .withEntry("data/minecraft/recipes/boat.json", "b")
                .write(dir.resolve("modb.jar"));

        List<Finding> findings = new ResourceOverridePass().analyze(scan(dir));

        assertEquals(1, findings.size());
        assertEquals(List.of("moda", "modb"), findings.get(0).mods());
        assertTrue(findings.get(0).problem().contains("2 identical resource files"));
    }

    @Test
    void mergedResourcesLikeLangAndTagsAreNotCollisions(@TempDir Path dir) throws IOException {
        new DeepJars.ModJar("moda", "1.0.0")
                .withEntry("assets/minecraft/lang/en_us.json", "a")
                .withEntry("data/minecraft/tags/items/logs.json", "a")
                .write(dir.resolve("moda.jar"));
        new DeepJars.ModJar("modb", "1.0.0")
                .withEntry("assets/minecraft/lang/en_us.json", "b")
                .withEntry("data/minecraft/tags/items/logs.json", "b")
                .write(dir.resolve("modb.jar"));

        assertTrue(new ResourceOverridePass().analyze(scan(dir)).isEmpty());
    }

    @Test
    void sameNestedLibraryAtDifferentMajorsIsFlagged(@TempDir Path dir) throws IOException {
        SyntheticJars.writeFabricJarWithNested(dir.resolve("moda.jar"), "moda", "1.0.0", "somelib", "1.2.0");
        SyntheticJars.writeFabricJarWithNested(dir.resolve("modb.jar"), "modb", "1.0.0", "somelib", "2.0.1");

        List<Finding> findings = new BundledLibraryClashPass().analyze(scan(dir));

        assertEquals(1, findings.size());
        assertEquals("bundled-library-clash", findings.get(0).type());
        assertEquals(List.of("moda", "modb"), findings.get(0).mods());
    }

    @Test
    void sameNestedLibraryAtSameMajorIsFine(@TempDir Path dir) throws IOException {
        SyntheticJars.writeFabricJarWithNested(dir.resolve("moda.jar"), "moda", "1.0.0", "somelib", "1.2.0");
        SyntheticJars.writeFabricJarWithNested(dir.resolve("modb.jar"), "modb", "1.0.0", "somelib", "1.5.3");

        assertTrue(new BundledLibraryClashPass().analyze(scan(dir)).isEmpty());
    }

    @Test
    void differingWideningsOfTheSameMemberAreFlagged(@TempDir Path dir) throws IOException {
        new DeepJars.ModJar("moda", "1.0.0")
                .withAccessWidener("accessWidener v2 named\naccessible method net/minecraft/class_761 method_3251 ()V\n")
                .write(dir.resolve("moda.jar"));
        new DeepJars.ModJar("modb", "1.0.0")
                .withAccessWidener("accessWidener v2 named\ntransitive-extendable method net/minecraft/class_761 method_3251 ()V\n")
                .write(dir.resolve("modb.jar"));

        List<Finding> findings = new AccessWidenerConflictPass().analyze(scan(dir));

        assertEquals(1, findings.size());
        assertEquals("access-widener-conflict", findings.get(0).type());
        assertEquals(Severity.LOW, findings.get(0).severity());
    }

    @Test
    void subsetWideningsAreHarmless(@TempDir Path dir) throws IOException {
        new DeepJars.ModJar("moda", "1.0.0")
                .withAccessWidener("accessWidener v2 named\n"
                        + "accessible field net/minecraft/class_1853 field_9013 Ljava/util/Map;\n"
                        + "mutable field net/minecraft/class_1853 field_9013 Ljava/util/Map;\n")
                .write(dir.resolve("moda.jar"));
        new DeepJars.ModJar("modb", "1.0.0")
                .withAccessWidener("accessWidener v2 named\n"
                        + "accessible field net/minecraft/class_1853 field_9013 Ljava/util/Map;\n")
                .write(dir.resolve("modb.jar"));

        assertTrue(new AccessWidenerConflictPass().analyze(scan(dir)).isEmpty());
    }

    @Test
    void identicalWideningsAreHarmless(@TempDir Path dir) throws IOException {
        String widener = "accessWidener v2 named\naccessible method net/minecraft/class_761 method_3251 ()V\n";
        new DeepJars.ModJar("moda", "1.0.0").withAccessWidener(widener).write(dir.resolve("moda.jar"));
        new DeepJars.ModJar("modb", "1.0.0").withAccessWidener(widener).write(dir.resolve("modb.jar"));

        assertTrue(new AccessWidenerConflictPass().analyze(scan(dir)).isEmpty());
    }
}
