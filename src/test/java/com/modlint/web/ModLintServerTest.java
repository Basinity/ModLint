package com.modlint.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modlint.testutil.FixtureJars;
import io.javalin.Javalin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModLintServerTest {

    private static final String BOUNDARY = "modlint-test-boundary";

    @Test
    void analyzesUploadedJars(@TempDir Path dir) throws Exception {
        Path iris = dir.resolve("iris.jar");
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"), iris);

        HttpResponse<String> response = post(Limits.defaults(),
                multipart(part("iris.jar", Files.readAllBytes(iris)), field("mcVersion", "1.20.1")));

        assertEquals(200, response.statusCode());
        JsonObject report = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, report.get("jars").getAsInt());
        assertEquals("1.20.1", report.get("minecraftVersion").getAsString());
        assertTrue(response.body().contains("missing-dependency"));
    }

    @Test
    void analyzesJarsInsideAZip(@TempDir Path dir) throws Exception {
        Path iris = dir.resolve("iris.jar");
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"), iris);
        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
            zip.putNextEntry(new ZipEntry("mods/iris.jar"));
            zip.write(Files.readAllBytes(iris));
            zip.closeEntry();
        }

        HttpResponse<String> response = post(Limits.defaults(),
                multipart(part("mods.zip", zipBytes.toByteArray())));

        assertEquals(200, response.statusCode());
        JsonObject report = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, report.get("jars").getAsInt());
        assertTrue(response.body().contains("missing-dependency"));
    }

    @Test
    void uppercaseJarExtensionIsStillAnalyzed(@TempDir Path dir) throws Exception {
        Path iris = dir.resolve("iris.jar");
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"), iris);

        HttpResponse<String> response = post(Limits.defaults(),
                multipart(part("IRIS-1.7.6.JAR", Files.readAllBytes(iris))));

        assertEquals(200, response.statusCode());
        JsonObject report = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, report.get("jars").getAsInt());
        assertTrue(response.body().contains("missing-dependency"));
    }

    @Test
    void rejectsFilesThatAreNeitherJarNorZip() throws Exception {
        HttpResponse<String> response = post(Limits.defaults(),
                multipart(part("pack.mrpack", new byte[] {1, 2, 3})));

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    @Test
    void rejectsUploadsPastTheJarCountCap(@TempDir Path dir) throws Exception {
        Path iris = dir.resolve("iris.jar");
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"), iris);
        byte[] jar = Files.readAllBytes(iris);
        Limits oneJarOnly = new Limits(1, Long.MAX_VALUE, Long.MAX_VALUE,
                Duration.ofSeconds(60), 100, Duration.ofMinutes(10), 2);

        HttpResponse<String> response = post(oneJarOnly,
                multipart(part("a.jar", jar), part("b.jar", jar)));

        assertEquals(413, response.statusCode());
    }

    @Test
    void rateLimitsRepeatedAnalyses(@TempDir Path dir) throws Exception {
        Path iris = dir.resolve("iris.jar");
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"), iris);
        byte[] body = multipart(part("iris.jar", Files.readAllBytes(iris)));
        Limits oneRequest = new Limits(500, Long.MAX_VALUE, Long.MAX_VALUE,
                Duration.ofSeconds(60), 1, Duration.ofMinutes(10), 2);

        Javalin app = new ModLintServer(oneRequest).createApp().start("127.0.0.1", 0);
        try {
            assertEquals(200, send(app.port(), body).statusCode());
            assertEquals(429, send(app.port(), body).statusCode());
        } finally {
            app.stop();
        }
    }

    private static HttpResponse<String> post(Limits limits, byte[] body) throws Exception {
        Javalin app = new ModLintServer(limits).createApp().start("127.0.0.1", 0);
        try {
            return send(app.port(), body);
        } finally {
            app.stop();
        }
    }

    private static HttpResponse<String> send(int port, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/analyze"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] part(String filename, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static byte[] field(String name, String value) {
        return ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] multipart(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part);
        }
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
