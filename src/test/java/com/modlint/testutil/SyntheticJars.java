package com.modlint.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Builds minimal synthetic Fabric mod jars for cases no real fixture covers. */
public final class SyntheticJars {

    private SyntheticJars() {
    }

    /** Writes a jar whose {@code fabric.mod.json} declares just an id and version. */
    public static void writeFabricJar(Path jar, String id, String version) throws IOException {
        writeFabricJar(jar, id, version, "");
    }

    /** Writes a jar whose {@code fabric.mod.json} carries extra JSON after id and version. */
    public static void writeFabricJar(Path jar, String id, String version, String extraJson) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            writeMetadata(out, metadata(id, version, extraJson));
        }
    }

    /** Writes a jar bundling one nested jar-in-jar mod under {@code META-INF/jars/}. */
    public static void writeFabricJarWithNested(Path jar, String id, String version,
                                                String nestedId, String nestedVersion) throws IOException {
        String nestedPath = "META-INF/jars/" + nestedId + ".jar";
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes)) {
            writeMetadata(nested, metadata(nestedId, nestedVersion, ""));
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            writeMetadata(out, metadata(id, version,
                    ", \"jars\": [{ \"file\": \"" + nestedPath + "\" }]"));
            out.putNextEntry(new JarEntry(nestedPath));
            out.write(nestedBytes.toByteArray());
            out.closeEntry();
        }
    }

    /** Writes a jar carrying only Forge metadata. */
    public static void writeForgeJar(Path jar, String id) throws IOException {
        writeTomlJar(jar, "META-INF/mods.toml",
                "modLoader=\"javafml\"\n[[mods]]\nmodId=\"" + id + "\"\n");
    }

    /** Writes a jar carrying the given TOML at the given metadata entry, and nothing else. */
    public static void writeTomlJar(Path jar, String metadataEntry, String toml) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(metadataEntry));
            out.write(toml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    /** A minimal mods.toml declaring one mod with a version and the given extra TOML after it. */
    public static String forgeToml(String id, String version, String extraToml) {
        return "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"MIT\"\n"
                + "[[mods]]\nmodId=\"" + id + "\"\nversion=\"" + version + "\"\n" + extraToml;
    }

    /** One {@code [[dependencies.<id>]]} table in Forge's mandatory=true style. */
    public static String forgeDependency(String modId, String depId, String versionRange) {
        return "[[dependencies." + modId + "]]\nmodId=\"" + depId + "\"\nmandatory=true\n"
                + (versionRange == null ? "" : "versionRange=\"" + versionRange + "\"\n")
                + "side=\"BOTH\"\n";
    }

    /** Writes a Forge jar bundling one nested jar-in-jar mod via {@code META-INF/jarjar/metadata.json}. */
    public static void writeForgeJarWithJarJar(Path jar, String id, String version,
                                               String nestedId, String nestedVersion) throws IOException {
        String nestedPath = "META-INF/jarjar/" + nestedId + ".jar";
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes)) {
            nested.putNextEntry(new JarEntry("META-INF/mods.toml"));
            nested.write(forgeToml(nestedId, nestedVersion, "").getBytes(StandardCharsets.UTF_8));
            nested.closeEntry();
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/mods.toml"));
            out.write(forgeToml(id, version, "").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("META-INF/jarjar/metadata.json"));
            out.write(("{\"jars\":[{\"identifier\":{\"group\":\"test\",\"artifact\":\"" + nestedId
                    + "\"},\"version\":{\"range\":\"[" + nestedVersion + ",)\",\"artifactVersion\":\""
                    + nestedVersion + "\"},\"path\":\"" + nestedPath + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry(nestedPath));
            out.write(nestedBytes.toByteArray());
            out.closeEntry();
        }
    }

    /** Writes a jar whose nested jars chain {@code depth} levels deep (ids {@code id-1} … {@code id-depth}). */
    public static void writeFabricJarWithNestedChain(Path jar, String id, int depth) throws IOException {
        byte[] child = null;
        String childPath = null;
        for (int level = depth; level >= 1; level--) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (JarOutputStream out = new JarOutputStream(bytes)) {
                writeLevel(out, id + "-" + level, child, childPath);
            }
            child = bytes.toByteArray();
            childPath = "META-INF/jars/" + id + "-" + level + ".jar";
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            writeLevel(out, id, child, childPath);
        }
    }

    private static void writeLevel(JarOutputStream out, String id, byte[] child, String childPath)
            throws IOException {
        String extraJson = child == null ? "" : ", \"jars\": [{ \"file\": \"" + childPath + "\" }]";
        writeMetadata(out, metadata(id, "1.0.0", extraJson));
        if (child != null) {
            out.putNextEntry(new JarEntry(childPath));
            out.write(child);
            out.closeEntry();
        }
    }

    private static String metadata(String id, String version, String extraJson) {
        return "{ \"schemaVersion\": 1, \"id\": \"" + id + "\", \"version\": \"" + version + "\"" + extraJson + " }";
    }

    private static void writeMetadata(JarOutputStream out, String fabricModJson) throws IOException {
        out.putNextEntry(new JarEntry("fabric.mod.json"));
        out.write(fabricModJson.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
