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
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            writeMetadata(out, metadata(id, version, ""));
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

    private static String metadata(String id, String version, String extraJson) {
        return "{ \"schemaVersion\": 1, \"id\": \"" + id + "\", \"version\": \"" + version + "\"" + extraJson + " }";
    }

    private static void writeMetadata(JarOutputStream out, String fabricModJson) throws IOException {
        out.putNextEntry(new JarEntry("fabric.mod.json"));
        out.write(fabricModJson.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
