package com.modlint.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.modlint.core.model.ModInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FabricModMetadataParserTest {

    private final FabricModMetadataParser parser = new FabricModMetadataParser();

    @Test
    void parsesIdVersionAndName() {
        String json = """
            {
              "schemaVersion": 1,
              "id": "examplemod",
              "version": "1.2.3",
              "name": "Example Mod"
            }
            """;
        ModInfo mod = parser.parse(json);
        assertEquals("examplemod", mod.id());
        assertEquals("1.2.3", mod.version());
        assertEquals("Example Mod", mod.name());
    }

    @Test
    void defaultsNameToIdWhenAbsent() {
        String json = """
            { "schemaVersion": 1, "id": "examplemod", "version": "1.0.0" }
            """;
        assertEquals("examplemod", parser.parse(json).name());
    }

    @Test
    void rejectsMetadataMissingId() {
        String json = """
            { "schemaVersion": 1, "version": "1.0.0" }
            """;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @Test
    void parsesRelationsWithStringAndArrayRanges() {
        String json = """
            {
              "schemaVersion": 1,
              "id": "examplemod",
              "version": "1.0.0",
              "depends": {
                "minecraft": ">=1.20 <1.21",
                "sodium": ["0.5.x", "0.6.x"]
              },
              "breaks": { "optifabric": "*" },
              "conflicts": { "othermod": "<2.0.0" }
            }
            """;
        ModInfo mod = parser.parse(json);
        assertEquals(Map.of(
                "minecraft", List.of(">=1.20 <1.21"),
                "sodium", List.of("0.5.x", "0.6.x")), mod.depends());
        assertEquals(Map.of("optifabric", List.of("*")), mod.breaks());
        assertEquals(Map.of("othermod", List.of("<2.0.0")), mod.conflicts());
    }

    @Test
    void parsesProvides() {
        String json = """
            {
              "schemaVersion": 1,
              "id": "examplemod",
              "version": "1.0.0",
              "provides": ["examplemod_core", "examplemod_api"]
            }
            """;
        assertEquals(List.of("examplemod_core", "examplemod_api"), parser.parse(json).provides());
    }

    @Test
    void absentSectionsComeBackEmpty() {
        String json = """
            { "schemaVersion": 1, "id": "examplemod", "version": "1.0.0" }
            """;
        ModInfo mod = parser.parse(json);
        assertEquals(List.of(), mod.provides());
        assertEquals(Map.of(), mod.depends());
        assertEquals(Map.of(), mod.breaks());
        assertEquals(Map.of(), mod.conflicts());
    }
}
