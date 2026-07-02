# Conflict fixtures

Real conflict cases for the analysis passes, gathered from live modpacks. Each directory under a case is the content of one mod jar, holding only the byte-exact metadata extracted from the real jar (never the jar itself, whose code is not ours to redistribute). Tests pack a directory into a jar with `FixtureJars.packJar` before feeding it to the engine.

## missing-dependency

`iris` declares `"depends": { "sodium": ["0.5.x"] }`, and no jar in the set provides `sodium`. Expected finding: missing dependency `sodium` for `iris`. Its only other dependencies are `minecraft` and `fabricloader`, which the resolver must treat as platform-provided and always present, so `sodium` is the single expected finding.

## version-range-violation

`iris` 1.7.6 declares `"depends": { "sodium": ["0.5.x"] }`, but the set contains sodium 0.6.13. The dependency is present yet outside the declared range. Expected finding: version range violation on `sodium` for `iris`. The iris metadata also pins `"minecraft": ["1.20.1"]` while sodium 0.6.13 targets 1.21.1, so a pass that checks the Minecraft version of the set can surface a second finding here.

## declared-breaks

`sodium` 0.8.12 declares `"breaks": { "iris": "<=1.10.6" }`, and the set contains iris 1.10.4, inside the broken range. Expected finding: declared incompatibility between `sodium` and `iris`. The pair is otherwise compatible (iris 1.10.4 wants `sodium 0.8.x`), so this is the only expected finding.

## wrong-loader

`jei` (Just Enough Items) in its Forge build: the jar carries `META-INF/mods.toml` and no `fabric.mod.json`. Expected finding in a Fabric set: wrong-loader jar. Caveat learned from the source packs: compatibility layers make cross-loader jars intentional (one pack runs Forge mods on Fabric through Kilt, another runs Fabric mods on Forge through Sinytra Connector), so the wrong-loader pass should downgrade or suppress the finding when such a layer is present in the set.

## Provenance

| Fixture jar | Source |
|---|---|
| iris-1.7.6+mc1.20.1 | a live Fabric modpack (MC 1.20.1) |
| sodium-fabric-0.6.13+mc1.21.1 | a live Fabric modpack (MC 1.21.1) |
| sodium-fabric-0.8.12+mc1.21.11 | a live Fabric instance (MC 1.21.11) |
| iris-fabric-1.10.4+mc1.21.11 | a live Fabric modpack (MC 1.21.11) |
| jei-1.20.1-forge-15.20.0.130 | a live Forge modpack (MC 1.20.1) |

The two mods in `version-range-violation` and the sodium pair across `version-range-violation` / `declared-breaks` come from packs for different Minecraft versions; combining them is what seeds the conflict. Combining the two sodium versions in one set also gives a duplicate-mod-id case for free.
