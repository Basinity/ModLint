package com.modlint.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.modlint.core.model.ModInfo;
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
}
