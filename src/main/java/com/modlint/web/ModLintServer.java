package com.modlint.web;

import com.google.gson.Gson;
import com.modlint.core.report.Report;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import java.util.Map;
import java.util.Optional;

/**
 * The ModLint web front-end: serves the browser UI and exposes the engine as
 * {@code POST /api/analyze} (multipart {@code files} of jars or a mods-folder zip, optional
 * {@code mcVersion} field), answering with the same JSON report the CLI's {@code --json} emits.
 * Binds to localhost by default; a reverse proxy terminates TLS in front of it.
 */
public final class ModLintServer {

    private static final Gson GSON = new Gson();

    private final Limits limits;
    private final AnalysisService service;
    private final RateLimiter rateLimiter;

    public ModLintServer(Limits limits) {
        this.limits = limits;
        this.service = new AnalysisService(limits);
        this.rateLimiter = new RateLimiter(limits.analysesPerWindow(), limits.window());
    }

    public static void main(String[] args) {
        String host = System.getenv().getOrDefault("MODLINT_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("MODLINT_PORT", "8080"));
        new ModLintServer(Limits.defaults()).createApp().start(host, port);
    }

    public Javalin createApp() {
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            if (ModLintServer.class.getResource("/web") != null) {
                config.staticFiles.add("/web", Location.CLASSPATH);
            }
            config.jetty.multipartConfig.maxFileSize(limits.maxTotalBytes(), SizeUnit.BYTES);
            config.jetty.multipartConfig.maxTotalRequestSize(limits.maxTotalBytes(), SizeUnit.BYTES);
            config.jetty.multipartConfig.maxInMemoryFileSize(1, SizeUnit.MB);
        });
        app.post("/api/analyze", this::analyze);
        app.exception(WebInputException.class, (e, ctx) -> error(ctx, e.status(), e.getMessage()));
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            error(ctx, 500, "The analysis failed unexpectedly.");
        });
        return app;
    }

    private void analyze(Context ctx) throws Exception {
        if (!rateLimiter.allow(clientIp(ctx))) {
            throw new WebInputException(429, "Too many analyses in a short time. Wait a few minutes.");
        }
        long declaredSize = Optional.ofNullable(ctx.header("Content-Length")).map(Long::parseLong).orElse(0L);
        if (declaredSize > limits.maxTotalBytes()) {
            throw new WebInputException(413, "The upload is larger than "
                    + (limits.maxTotalBytes() / (1024 * 1024)) + " MB.");
        }
        Optional<String> minecraftVersion = Optional.ofNullable(ctx.formParam("mcVersion"))
                .map(String::trim).filter(version -> !version.isEmpty());
        Report report = service.analyze(ctx.uploadedFiles("files"), minecraftVersion);
        ctx.contentType("application/json").result(report.toJson());
    }

    /** The proxy's forwarded client address when present, so rate limiting keys on real clients. */
    private static String clientIp(Context ctx) {
        String forwarded = ctx.header("X-Forwarded-For");
        return forwarded == null ? ctx.ip() : forwarded.split(",")[0].trim();
    }

    private static void error(Context ctx, int status, String message) {
        ctx.status(status).contentType("application/json").result(GSON.toJson(Map.of("error", message)));
    }
}
