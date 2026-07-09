package com.modlint.core.analysis;

import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import com.modlint.core.version.VersionRanges;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The scanned mods folder indexed for analysis: every id that counts as present (top-level
 * mods, their {@code provides} aliases, nested jar-in-jar mods and their aliases) mapped to
 * the provider the loader would resolve it to. When the same id is provided more than once,
 * Fabric Loader loads only the highest version, so version checks see just that winner.
 */
public final class ModSet {

    /** Ids the platform itself provides; they are never reported missing. */
    public static final Set<String> PLATFORM_IDS = Set.of("minecraft", "java", "fabricloader");

    /**
     * Ids fabric-loader itself bundles and registers, in a version tied to the loader install
     * rather than the mods folder. Stale nested copies lose to the loader's own, so neither
     * absence nor a version range can be judged from the folder alone.
     */
    public static final Set<String> LOADER_BUNDLED_IDS = Set.of("mixinextras");

    /** Mod ids of Forge-on-Fabric / Fabric-on-Forge compatibility layers. */
    private static final Set<String> COMPAT_LAYER_IDS = Set.of("kilt", "connector");

    /** One id made available by one mod: the providing mod's id and version. */
    public record Provider(String modId, String version) {
    }

    private final List<ScannedJar> jars;
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    private final Optional<String> minecraftVersion;

    public ModSet(List<ScannedJar> jars, Optional<String> minecraftVersion) {
        this.jars = List.copyOf(jars);
        this.minecraftVersion = minecraftVersion;
        for (ScannedJar jar : jars) {
            jar.fabricMod().ifPresent(this::index);
            jar.nestedMods().forEach(this::index);
        }
        minecraftVersion.ifPresent(version -> index("minecraft", new Provider("minecraft", version)));
    }

    private void index(ModInfo mod) {
        Provider provider = new Provider(mod.id(), mod.version());
        index(mod.id(), provider);
        for (String alias : mod.provides()) {
            index(alias, provider);
        }
    }

    private void index(String id, Provider candidate) {
        providers.merge(id, candidate, (current, added) ->
                VersionRanges.isNewer(added.version(), current.version()) ? added : current);
    }

    public List<ScannedJar> jars() {
        return jars;
    }

    /** The top-level Fabric mods, the ones whose declarations the passes evaluate. */
    public List<ModInfo> topLevelMods() {
        return jars.stream().flatMap(jar -> jar.fabricMod().stream()).toList();
    }

    /** The provider the loader would resolve {@code id} to, or empty when the id is absent. */
    public Optional<Provider> providerOf(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public Optional<String> minecraftVersion() {
        return minecraftVersion;
    }

    /** True when a cross-loader compatibility layer (Kilt, Sinytra Connector) is installed. */
    public boolean hasCompatLayer() {
        return COMPAT_LAYER_IDS.stream().anyMatch(id -> providerOf(id).isPresent());
    }

    /** Fewer foreign jars than this stay individual findings; a pack-level call needs a quorum. */
    private static final int FOREIGN_PACK_MIN = 3;

    /**
     * True when jars targeting other loaders outnumber the Fabric mods: the folder belongs to
     * a Forge/NeoForge pack, so Fabric-side dependency analysis has no valid premise (its jars
     * run through a compatibility layer whose Forge side this scan cannot read).
     */
    public boolean foreignDominant() {
        long fabric = jars.stream().filter(jar -> jar.fabricMod().isPresent()).count();
        long foreign = foreignJarCount();
        return foreign >= FOREIGN_PACK_MIN && foreign > fabric;
    }

    /** The jars with mod metadata for other loaders only. */
    public long foreignJarCount() {
        return jars.stream()
                .filter(jar -> !jar.loaders().isEmpty() && !jar.loaders().contains(ModLoader.FABRIC))
                .count();
    }
}
