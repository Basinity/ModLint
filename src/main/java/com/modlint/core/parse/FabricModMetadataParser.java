package com.modlint.core.parse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modlint.core.model.ModInfo;

/** Parses the contents of a {@code fabric.mod.json} into a {@link ModInfo}. */
public final class FabricModMetadataParser {

    /**
     * Parses a {@code fabric.mod.json} document. {@code name} falls back to {@code id} when absent,
     * matching how Fabric loader treats a mod with no display name.
     *
     * @throws IllegalArgumentException if a required field is missing
     */
    public ModInfo parse(String fabricModJson) {
        JsonObject root = JsonParser.parseString(fabricModJson).getAsJsonObject();
        String id = requireString(root, "id");
        String version = requireString(root, "version");
        String name = root.has("name") ? root.get("name").getAsString() : id;
        return new ModInfo(id, version, name);
    }

    private static String requireString(JsonObject root, String field) {
        if (!root.has(field)) {
            throw new IllegalArgumentException("fabric.mod.json is missing required field '" + field + "'");
        }
        return root.get(field).getAsString();
    }
}
