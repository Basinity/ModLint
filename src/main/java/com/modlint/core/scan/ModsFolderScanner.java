package com.modlint.core.scan;

import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import com.modlint.core.parse.FabricModMetadataParser;
import com.modlint.core.parse.JarModReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Scans a mods folder into one {@link ScannedJar} per jar file, ignoring everything else. */
public final class ModsFolderScanner {

    private final JarModReader reader;
    private final FabricModMetadataParser parser;

    public ModsFolderScanner() {
        this(new JarModReader(), new FabricModMetadataParser());
    }

    public ModsFolderScanner(JarModReader reader, FabricModMetadataParser parser) {
        this.reader = reader;
        this.parser = parser;
    }

    /**
     * Scans every {@code .jar} directly in {@code modsFolder}, sorted by file name.
     *
     * @throws IOException if the folder or a jar cannot be read, or a jar's Fabric metadata is invalid
     */
    public List<ScannedJar> scan(Path modsFolder) throws IOException {
        try (Stream<Path> files = Files.list(modsFolder)) {
            List<ScannedJar> jars = new ArrayList<>();
            for (Path jar : files.filter(f -> f.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                jars.add(scanJar(jar));
            }
            return List.copyOf(jars);
        }
    }

    private ScannedJar scanJar(Path jar) throws IOException {
        Set<ModLoader> loaders = reader.detectLoaders(jar);
        Optional<ModInfo> fabricMod = Optional.empty();
        if (loaders.contains(ModLoader.FABRIC)) {
            try {
                fabricMod = Optional.of(parser.parse(reader.readFabricMetadata(jar)));
            } catch (RuntimeException e) {
                throw new IOException(jar + " has invalid fabric.mod.json: " + e.getMessage(), e);
            }
        }
        return new ScannedJar(jar, loaders, fabricMod);
    }
}
