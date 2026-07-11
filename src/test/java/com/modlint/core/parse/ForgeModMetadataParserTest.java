package com.modlint.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ForgeModMetadataParserTest {

    private final ForgeModMetadataParser parser = new ForgeModMetadataParser();

    /** The shape of Alex's Mobs 1.22.9's real mods.toml, comments and all. */
    private static final String ALEXS_MOBS = """
            modLoader="javafml" #mandatory
            loaderVersion="[46,)" #mandatory
            license="GNU LESSER GENERAL PUBLIC LICENSE"
            [[mods]] #mandatory
            modId="alexsmobs" #mandatory
            version="1.22.9" #mandatory
            displayName="Alex's Mobs" #mandatory
            description='''New, original, engaging, and aesthetic mobs for Minecraft.'''
            [[dependencies.alexsmobs]] #optional
                modId="citadel" #mandatory
                mandatory=true #mandatory
                versionRange="[2.6.0,)" #mandatory
                ordering="AFTER"
                side="BOTH"
            [modproperties.alexsmobs]
                catalogueItemIcon="alexsmobs:bear_dust"
            [[dependencies.alexsmobs]]
                modId="forge"
                mandatory=true
                versionRange="[47.1.0,)"
                ordering="NONE"
                side="BOTH"
            """;

    @Test
    void parsesARealForgeModsToml() {
        List<ModInfo> mods = parser.parse(ALEXS_MOBS, ModLoader.FORGE, Optional.empty()).mods();

        assertEquals(1, mods.size());
        ModInfo mod = mods.get(0);
        assertEquals(ModLoader.FORGE, mod.loader());
        assertEquals("alexsmobs", mod.id());
        assertEquals("1.22.9", mod.version());
        assertEquals("Alex's Mobs", mod.name());
        assertEquals(List.of("[2.6.0,)"), mod.depends().get("citadel"));
        assertEquals(List.of("[47.1.0,)"), mod.depends().get("forge"));
    }

    @Test
    void substitutesTheManifestVersionForTheJarVersionPlaceholder() {
        String toml = "[[mods]]\nmodId=\"curios\"\nversion=\"${file.jarVersion}\"\n";

        assertEquals("5.14.1", parser.parse(toml, ModLoader.FORGE, Optional.of("5.14.1"))
                .mods().get(0).version());
        assertEquals("0.0NONE", parser.parse(toml, ModLoader.FORGE, Optional.empty())
                .mods().get(0).version());
    }

    @Test
    void mapsNeoForgeDependencyTypesOntoRelations() {
        String toml = """
                [[mods]]
                modId="somemod"
                version="1.0.0"
                [[dependencies.somemod]]
                    modId="requiredlib"
                    type="required"
                    versionRange="[3.0,)"
                [[dependencies.somemod]]
                    modId="oldenemy"
                    type="incompatible"
                    versionRange="[1.0,2.0)"
                [[dependencies.somemod]]
                    modId="grumpyneighbor"
                    type="discouraged"
                [[dependencies.somemod]]
                    modId="nicetohave"
                    type="optional"
                """;
        ModInfo mod = parser.parse(toml, ModLoader.NEOFORGE, Optional.empty()).mods().get(0);

        assertEquals(List.of("[3.0,)"), mod.depends().get("requiredlib"));
        assertEquals(List.of("[1.0,2.0)"), mod.breaks().get("oldenemy"));
        assertEquals(List.of(), mod.conflicts().get("grumpyneighbor"));
        assertTrue(!mod.depends().containsKey("nicetohave"));
    }

    @Test
    void sideSpecificDependenciesNeverBecomeRelations() {
        String toml = """
                [[mods]]
                modId="somemod"
                version="1.0.0"
                [[dependencies.somemod]]
                    modId="clientlib"
                    mandatory=true
                    side="CLIENT"
                """;
        ModInfo mod = parser.parse(toml, ModLoader.FORGE, Optional.empty()).mods().get(0);

        assertTrue(mod.depends().isEmpty());
    }

    @Test
    void parsesSeveralModsWithTheirOwnDependencies() {
        String toml = """
                [[mods]]
                modId="first"
                version="1.0.0"
                [[mods]]
                modId="second"
                version="2.0.0"
                [[dependencies.second]]
                    modId="firstlib"
                    mandatory=true
                    versionRange="[1.0,)"
                """;
        List<ModInfo> mods = parser.parse(toml, ModLoader.FORGE, Optional.empty()).mods();

        assertEquals(2, mods.size());
        assertTrue(mods.get(0).depends().isEmpty());
        assertEquals(List.of("[1.0,)"), mods.get(1).depends().get("firstlib"));
    }

    @Test
    void collectsNeoForgeMixinConfigEntries() {
        String toml = """
                [[mods]]
                modId="somemod"
                version="1.0.0"
                [[mixins]]
                config="somemod.mixins.json"
                [[mixins]]
                config="somemod-extra.mixins.json"
                """;

        assertEquals(List.of("somemod.mixins.json", "somemod-extra.mixins.json"),
                parser.parse(toml, ModLoader.NEOFORGE, Optional.empty()).mixinConfigs());
    }

    @Test
    void missingModIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("[[mods]]\nversion=\"1.0\"\n", ModLoader.FORGE, Optional.empty()));
    }

    @Test
    void malformedTomlIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("[[mods\nmodId=", ModLoader.FORGE, Optional.empty()));
    }
}
