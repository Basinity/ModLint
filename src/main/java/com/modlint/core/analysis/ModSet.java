package com.modlint.core.analysis;

import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import com.modlint.core.version.VersionRanges;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The scanned mods folder indexed for analysis under one target loader: every id that counts
 * as present (top-level mods, their {@code provides} aliases, nested jar-in-jar mods and
 * their aliases) mapped to the provider the loader would resolve it to. When the same id is
 * provided more than once, the loader loads only the highest version, so version checks see
 * just that winner.
 *
 * <p>The target loader is either given explicitly or detected as the loader most of the
 * jars carry metadata for. Only mods the target loader would load count; jars targeting
 * other loaders are the wrong-loader pass's business.
 */
public final class ModSet {

    /**
     * Ids the loaders themselves bundle and register, in a version tied to the loader install
     * rather than the mods folder. Stale nested copies lose to the loader's own, so neither
     * absence nor a version range can be judged from the folder alone.
     */
    public static final Set<String> LOADER_BUNDLED_IDS = Set.of("mixinextras");

    /** Mod ids of Forge-on-Fabric / Fabric-on-Forge compatibility layers. */
    private static final Set<String> COMPAT_LAYER_IDS = Set.of("kilt", "connector", "connectormod");

    /** One id made available by one mod: the providing mod's id and version. */
    public record Provider(String modId, String version) {
    }

    private final List<ScannedJar> jars;
    private final ModLoader targetLoader;
    private final Set<ModLoader> acceptedLoaders;
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    private final Optional<String> minecraftVersion;

    /** Indexes under the auto-detected target loader. */
    public ModSet(List<ScannedJar> jars, Optional<String> minecraftVersion) {
        this(jars, minecraftVersion, detectTargetLoader(jars));
    }

    public ModSet(List<ScannedJar> jars, Optional<String> minecraftVersion, ModLoader targetLoader) {
        this.jars = List.copyOf(jars);
        this.minecraftVersion = minecraftVersion;
        this.targetLoader = targetLoader;
        this.acceptedLoaders = acceptedLoaders(targetLoader, minecraftVersion);
        for (ScannedJar jar : jars) {
            nativeMods(jar).forEach(this::index);
            jar.nestedMods().stream()
                    .filter(mod -> acceptedLoaders.contains(mod.loader()))
                    .forEach(this::index);
        }
        minecraftVersion.ifPresent(version -> index("minecraft", new Provider("minecraft", version)));
    }

    /**
     * The loader most jars carry metadata for. Fabric wins ties (the primary target);
     * NeoForge wins over Forge, since Forge-era jars usually carry both markers.
     */
    public static ModLoader detectTargetLoader(List<ScannedJar> jars) {
        long fabric = countTargeting(jars, ModLoader.FABRIC);
        long forge = countTargeting(jars, ModLoader.FORGE);
        long neoforge = countTargeting(jars, ModLoader.NEOFORGE);
        if (fabric >= forge && fabric >= neoforge) {
            return ModLoader.FABRIC;
        }
        return neoforge >= forge ? ModLoader.NEOFORGE : ModLoader.FORGE;
    }

    private static long countTargeting(List<ScannedJar> jars, ModLoader loader) {
        return jars.stream().filter(jar -> jar.loaders().contains(loader)).count();
    }

    /**
     * The loaders whose mods the target loader loads. NeoForge still loaded plain Forge
     * metadata through Minecraft 1.20.x; from 1.21 on it requires its own.
     */
    private static Set<ModLoader> acceptedLoaders(ModLoader target, Optional<String> minecraftVersion) {
        if (target == ModLoader.NEOFORGE
                && minecraftVersion.map(version -> version.startsWith("1.20")).orElse(true)) {
            return EnumSet.of(ModLoader.NEOFORGE, ModLoader.FORGE);
        }
        return EnumSet.of(target);
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

    public ModLoader targetLoader() {
        return targetLoader;
    }

    public Set<ModLoader> acceptedLoaders() {
        return acceptedLoaders;
    }

    /** Ids the platform itself provides under the target loader; they are never reported missing. */
    public Set<String> platformIds() {
        return switch (targetLoader) {
            case FABRIC, QUILT -> Set.of("minecraft", "java", "fabricloader");
            case FORGE -> Set.of("minecraft", "java", "forge");
            case NEOFORGE -> Set.of("minecraft", "java", "neoforge", "forge");
        };
    }

    /**
     * The mods of one jar the target loader would load: the mods declared for the target
     * itself, or failing that, for another accepted loader. Never both, so a jar carrying
     * Forge and NeoForge metadata for the same mod counts once.
     */
    public List<ModInfo> nativeMods(ScannedJar jar) {
        List<ModInfo> exact = jar.mods().stream().filter(mod -> mod.loader() == targetLoader).toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return jar.mods().stream().filter(mod -> acceptedLoaders.contains(mod.loader())).toList();
    }

    /** The jar's first native mod, the one deep findings are attributed to. */
    public Optional<ModInfo> primaryMod(ScannedJar jar) {
        return nativeMods(jar).stream().findFirst();
    }

    /** The top-level mods the target loader would load, the ones whose declarations the passes evaluate. */
    public List<ModInfo> topLevelMods() {
        return jars.stream().flatMap(jar -> nativeMods(jar).stream()).toList();
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
}
