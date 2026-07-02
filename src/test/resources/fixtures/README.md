# Conflict fixtures

Real conflict cases for the analysis passes, gathered from live modpacks. Each directory under a case is the content of one mod jar, holding only the byte-exact metadata extracted from the real jar (never the jar itself, whose code is not ours to redistribute). Tests pack a directory into a jar with `FixtureJars.packJar` before feeding it to the engine.

## missing-dependency

`amendments` declares `"depends": { "moonlight": ">=1.20-2.16.0" }`, and no jar in the set provides `moonlight`. Expected finding: missing dependency `moonlight` for `amendments`. `fabric-api` is included so the mod's `"fabric": "*"` dependency is satisfied and `moonlight` is the only missing one (besides the platform-provided `minecraft` / `java` / `fabricloader` ids, which the resolver must treat as always present).

## version-range-violation

`iris` 1.7.6 declares `"depends": { "sodium": ["0.5.x"] }`, but the set contains sodium 0.6.13. The dependency is present yet outside the declared range. Expected finding: version range violation on `sodium` for `iris`. The iris metadata also pins `"minecraft": ["1.20.1"]` while sodium 0.6.13 targets 1.21.1, so a pass that checks the Minecraft version of the set can surface a second finding here.

## declared-breaks

`sodium` 0.8.12 declares `"breaks": { "iris": "<=1.10.6" }`, and the set contains iris 1.10.4, inside the broken range. Expected finding: declared incompatibility between `sodium` and `iris`. The pair is otherwise compatible (iris 1.10.4 wants `sodium 0.8.x`), so this is the only expected finding.

## wrong-loader

`corpse` is a Forge mod: it carries `META-INF/mods.toml` and no `fabric.mod.json`. Expected finding in a Fabric set: wrong-loader jar. Caveat learned from the source pack: it ran this jar on purpose through Kilt, a Forge-on-Fabric compatibility layer, so the wrong-loader pass should downgrade or suppress the finding when such a layer (Kilt, Sinytra Connector) is present in the set.

## Provenance

| Fixture jar | Source |
|---|---|
| amendments-1.20-2.2.5-fabric | Valhalla Revolutions pack (MC 1.20.1) |
| fabric-api-0.92.9+1.20.1 | Valhalla Revolutions pack (MC 1.20.1) |
| iris-1.7.6+mc1.20.1 | Valhalla Revolutions pack (MC 1.20.1) |
| sodium-fabric-0.6.13+mc1.21.1 | The Harpy Express pack (MC 1.21.1) |
| sodium-fabric-0.8.12+mc1.21.11 | Feather Client instance (MC 1.21.11) |
| iris-fabric-1.10.4+mc1.21.11 | Yet Another Bingo pack (MC 1.21.11) |
| corpse-forge-1.20.1-1.0.23 | Valhalla Revolutions pack (MC 1.20.1) |

The two mods in `version-range-violation` and the sodium pair across `version-range-violation` / `declared-breaks` come from packs for different Minecraft versions; combining them is what seeds the conflict. Combining the two sodium versions in one set also gives a duplicate-mod-id case for free.
