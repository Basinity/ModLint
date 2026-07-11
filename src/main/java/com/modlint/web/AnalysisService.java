package com.modlint.web;

import com.modlint.core.analysis.Analyzer;
import com.modlint.core.analysis.Finding;
import com.modlint.core.analysis.ModSet;
import com.modlint.core.model.ScannedJar;
import com.modlint.core.report.Report;
import com.modlint.core.scan.ModsFolderScanner;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one upload through the engine: materializes the files into a temp folder, scans and
 * analyzes them under the time and concurrency caps, and deletes the folder again. Uploads
 * only ever live in the temp folder for the duration of the analysis.
 */
final class AnalysisService {

    private final Limits limits;
    private final Semaphore slots;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "modlint-analysis");
        thread.setDaemon(true);
        return thread;
    });

    AnalysisService(Limits limits) {
        this.limits = limits;
        this.slots = new Semaphore(limits.concurrentAnalyses());
    }

    Report analyze(List<UploadedFile> files, Optional<String> minecraftVersion) throws IOException {
        if (!slots.tryAcquire()) {
            throw new WebInputException(503, "The server is busy analyzing other packs. Try again in a moment.");
        }
        try {
            Path workDir = Files.createTempDirectory("modlint-web-");
            try {
                Path modsFolder = new UploadMaterializer(limits).materialize(files, workDir);
                return analyzeWithTimeout(modsFolder, minecraftVersion);
            } finally {
                deleteRecursively(workDir);
            }
        } finally {
            slots.release();
        }
    }

    private Report analyzeWithTimeout(Path modsFolder, Optional<String> minecraftVersion) {
        Future<Report> analysis = executor.submit(() -> {
            List<ScannedJar> jars = new ModsFolderScanner().scan(modsFolder);
            ModSet mods = new ModSet(jars, minecraftVersion);
            List<Finding> findings = new Analyzer().analyze(mods);
            return Report.of(mods, findings);
        });
        try {
            return analysis.get(limits.analysisTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            analysis.cancel(true);
            throw new WebInputException(503, "The analysis took longer than "
                    + limits.analysisTimeout().toSeconds() + " seconds. Try a smaller pack.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException cause) {
                throw new WebInputException(422, withoutLocalPaths(cause.getMessage(), modsFolder));
            }
            throw new IllegalStateException("Analysis failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Analysis interrupted", e);
        }
    }

    /** Engine errors name jars by their temp path; the client should only see the file name. */
    private static String withoutLocalPaths(String message, Path modsFolder) {
        return message.replace(modsFolder.toString() + java.io.File.separator, "");
    }

    /** Best-effort: after a timeout the analysis thread may still hold a jar open. */
    private static void deleteRecursively(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    file.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    dir.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Leftovers sit in the OS temp folder; nothing to surface to the client.
        }
    }
}
