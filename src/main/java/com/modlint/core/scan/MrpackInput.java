package com.modlint.core.scan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a Modrinth {@code .mrpack} file into an analyzable mods folder: extracts the jars
 * bundled under the pack's overrides and downloads the remote ones (sha1-verified) from the
 * URLs in {@code modrinth.index.json}. The index's declared Minecraft version rides along.
 */
public final class MrpackInput {

    /** An extracted-and-downloaded pack: the mods folder plus the index's Minecraft version. */
    public record Materialized(Path modsFolder, Optional<String> minecraftVersion) {
    }

    private static final String INDEX_ENTRY = "modrinth.index.json";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** Materializes {@code mrpack} into {@code workDir/mods}, downloading what is remote. */
    public Materialized materialize(Path mrpack, Path workDir) throws IOException {
        Path modsFolder = Files.createDirectories(workDir.resolve("mods"));
        try (ZipFile zip = new ZipFile(mrpack.toFile())) {
            ZipEntry indexEntry = zip.getEntry(INDEX_ENTRY);
            if (indexEntry == null) {
                throw new IOException(mrpack + " has no " + INDEX_ENTRY + " (not a Modrinth pack?)");
            }
            JsonObject index;
            try (InputStream in = zip.getInputStream(indexEntry)) {
                index = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
            }
            extractOverrideMods(zip, modsFolder);
            downloadRemoteMods(index, modsFolder);
            return new Materialized(modsFolder, minecraftVersion(index));
        }
    }

    private static Optional<String> minecraftVersion(JsonObject index) {
        if (!index.has("dependencies")) {
            return Optional.empty();
        }
        JsonObject dependencies = index.getAsJsonObject("dependencies");
        return dependencies.has("minecraft")
                ? Optional.of(dependencies.get("minecraft").getAsString())
                : Optional.empty();
    }

    /** Copies every jar under {@code overrides/mods/} or {@code client-overrides/mods/} out of the zip. */
    private static void extractOverrideMods(ZipFile zip, Path modsFolder) throws IOException {
        for (var entries = zip.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            boolean overrideMod = (name.startsWith("overrides/mods/") || name.startsWith("client-overrides/mods/"))
                    && name.endsWith(".jar") && !entry.isDirectory();
            if (!overrideMod) {
                continue;
            }
            // Resolve by base name only, so a crafted entry path can never escape the mods folder.
            Path target = modsFolder.resolve(Path.of(name).getFileName().toString()).normalize();
            if (!target.startsWith(modsFolder)) {
                throw new IOException("Unsafe zip entry path: " + name);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, target);
            }
        }
    }

    private static void downloadRemoteMods(JsonObject index, Path modsFolder) throws IOException {
        if (!index.has("files")) {
            return;
        }
        for (JsonElement element : index.getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            String path = file.get("path").getAsString();
            if (!path.startsWith("mods/") || !path.endsWith(".jar")) {
                continue;
            }
            Path target = modsFolder.resolve(Path.of(path).getFileName().toString()).normalize();
            if (!target.startsWith(modsFolder)) {
                throw new IOException("Unsafe file path in index: " + path);
            }
            String url = file.getAsJsonArray("downloads").get(0).getAsString();
            String expectedSha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
            System.err.println("Downloading " + target.getFileName());
            byte[] bytes = download(url);
            if (!sha1(bytes).equalsIgnoreCase(expectedSha1)) {
                throw new IOException(target.getFileName() + " failed sha1 verification after download");
            }
            Files.write(target, bytes);
        }
    }

    private static byte[] download(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2)).GET().build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted downloading " + url, e);
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
