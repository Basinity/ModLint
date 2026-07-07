package com.modlint.web;

import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns uploaded files into an analyzable mods folder. Accepts {@code .jar} files and
 * {@code .zip} archives of a mods folder (jars inside are extracted flat, by base name only,
 * so entry paths can never escape). Everything is written under the given work directory,
 * counted against the size caps, and belongs to the caller to delete afterwards.
 */
final class UploadMaterializer {

    private final Limits limits;

    UploadMaterializer(Limits limits) {
        this.limits = limits;
    }

    /** Materializes the uploads into {@code workDir/mods} and returns that folder. */
    Path materialize(List<UploadedFile> files, Path workDir) throws IOException {
        if (files.isEmpty()) {
            throw new WebInputException(400, "No files uploaded. Add .jar files or a .zip of your mods folder.");
        }
        Path modsFolder = Files.createDirectories(workDir.resolve("mods"));
        Budget budget = new Budget();
        for (UploadedFile file : files) {
            String name = baseName(file.filename());
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jar")) {
                try (InputStream in = file.content()) {
                    copyCapped(in, uniqueTarget(modsFolder, name), name, budget);
                }
            } else if (lower.endsWith(".zip")) {
                extractJars(file, modsFolder, budget);
            } else {
                throw new WebInputException(400, name + " is not a .jar or .zip. "
                        + "Upload mod jars or a zip of your mods folder (.mrpack works in the CLI).");
            }
        }
        try (var jars = Files.list(modsFolder)) {
            if (jars.findAny().isEmpty()) {
                throw new WebInputException(400, "The upload contains no .jar files.");
            }
        }
        return modsFolder;
    }

    private void extractJars(UploadedFile zip, Path modsFolder, Budget budget) throws IOException {
        try (ZipInputStream in = new ZipInputStream(zip.content())) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                String name = baseName(entry.getName());
                if (entry.isDirectory() || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                copyCapped(in, uniqueTarget(modsFolder, name), name, budget);
            }
        }
    }

    /** The base file name only, so neither an upload name nor a zip entry path can point elsewhere. */
    private static String baseName(String name) {
        String normalized = name.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static Path uniqueTarget(Path modsFolder, String name) {
        Path target = modsFolder.resolve(name);
        for (int counter = 2; Files.exists(target); counter++) {
            target = modsFolder.resolve(counter + "-" + name);
        }
        return target;
    }

    private void copyCapped(InputStream in, Path target, String name, Budget budget) throws IOException {
        budget.jars++;
        if (budget.jars > limits.maxJars()) {
            throw new WebInputException(413, "More than " + limits.maxJars() + " jars in one upload.");
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long written = 0;
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                written += read;
                budget.totalBytes += read;
                if (written > limits.maxJarBytes()) {
                    throw new WebInputException(413, name + " is larger than "
                            + (limits.maxJarBytes() / (1024 * 1024)) + " MB.");
                }
                if (budget.totalBytes > limits.maxTotalBytes()) {
                    throw new WebInputException(413, "The upload unpacks past "
                            + (limits.maxTotalBytes() / (1024 * 1024)) + " MB in total.");
                }
                out.write(buffer, 0, read);
            }
        }
    }

    /** Running totals across one upload's files and zip entries. */
    private static final class Budget {
        int jars;
        long totalBytes;
    }
}
