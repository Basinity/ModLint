package com.modlint.core.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses the contents of a {@code fabric.mod.json} into a {@link ModInfo}. */
public final class FabricModMetadataParser {

    /**
     * Parses a {@code fabric.mod.json} document. {@code name} falls back to {@code id} when absent,
     * matching how Fabric loader treats a mod with no display name. Absent {@code provides} and
     * relation sections come back empty, never null.
     *
     * @throws IllegalArgumentException if a required field is missing
     */
    public ModInfo parse(String fabricModJson) {
        JsonObject root = JsonParser.parseString(fabricModJson).getAsJsonObject();
        String id = requireString(root, "id");
        String version = requireString(root, "version");
        String name = root.has("name") ? root.get("name").getAsString() : id;
        return new ModInfo(ModLoader.FABRIC, id, version, name,
                stringList(root, "provides"),
                relationMap(root, "depends"),
                relationMap(root, "breaks"),
                relationMap(root, "conflicts"),
                nestedJarPaths(root),
                mixinConfigs(root),
                root.has("accessWidener") ? root.get("accessWidener").getAsString() : null);
    }

    /** Reads the {@code mixins} section, whose entries are strings or {@code {"config": ...}} objects. */
    private static List<String> mixinConfigs(JsonObject root) {
        if (!root.has("mixins")) {
            return List.of();
        }
        List<String> configs = new ArrayList<>();
        for (JsonElement entry : root.getAsJsonArray("mixins")) {
            configs.add(entry.isJsonObject()
                    ? entry.getAsJsonObject().get("config").getAsString()
                    : entry.getAsString());
        }
        return List.copyOf(configs);
    }

    /** Reads the {@code jars} section, a list of {@code {"file": "META-INF/jars/..."}} objects. */
    private static List<String> nestedJarPaths(JsonObject root) {
        if (!root.has("jars")) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (JsonElement entry : root.getAsJsonArray("jars")) {
            paths.add(entry.getAsJsonObject().get("file").getAsString());
        }
        return List.copyOf(paths);
    }

    private static String requireString(JsonObject root, String field) {
        if (!root.has(field)) {
            throw new IllegalArgumentException("fabric.mod.json is missing required field '" + field + "'");
        }
        return root.get(field).getAsString();
    }

    private static List<String> stringList(JsonObject root, String field) {
        if (!root.has(field)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray(field)) {
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    /** Reads a relation section ({@code depends} etc.), where each value is a string or an array of strings. */
    private static Map<String, List<String>> relationMap(JsonObject root, String field) {
        if (!root.has(field)) {
            return Map.of();
        }
        Map<String, List<String>> relations = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(field).entrySet()) {
            JsonElement value = entry.getValue();
            List<String> ranges;
            if (value.isJsonArray()) {
                ranges = new ArrayList<>();
                for (JsonElement range : value.getAsJsonArray()) {
                    ranges.add(range.getAsString());
                }
                ranges = List.copyOf(ranges);
            } else {
                ranges = List.of(value.getAsString());
            }
            relations.put(entry.getKey(), ranges);
        }
        return Collections.unmodifiableMap(relations);
    }
}
