# How the Mixin overlap detection works

Mixin conflicts are the modpack failures with the worst diagnostics. Two mods patch the same vanilla method, the pack crashes at launch or misbehaves in game, and the log points at whichever mod happened to apply last rather than at the pair. This document explains how ModLint finds these overlaps statically, from the jars alone, and why the result is reported as a potential finding rather than a proven conflict.

## Background: what a Mixin is

Mixin is the bytecode-patching framework most Minecraft mods use to change vanilla behavior. A mod ships Mixin classes: ordinary compiled classes annotated with `@Mixin(TargetClass.class)`, whose annotated methods are merged into the target class when the game loads. The framework applies every mod's Mixins to the same vanilla classes, one after another. Most of the time that composes fine. It stops composing when two mods rewrite the same code.

## Intrusive injectors only

Each method in a Mixin class declares how it patches the target through an injector annotation. They differ sharply in how well they tolerate company:

- `@Inject` adds a callback at a point in the method. Any number of mods can inject callbacks into the same method; the framework stacks them. This is the well-behaved case, and ModLint ignores it.
- `@Overwrite` replaces the entire target method. Two overwrites of the same method cannot both survive; one wins.
- `@Redirect` replaces a single call or field access inside the method. Two redirects of the same instruction are a hard conflict for the framework.
- `@ModifyConstant` rewrites constant values inside the method, which the framework implements like a redirect, with the same appetite for conflict.
- `@ModifyArg` and `@ModifyArgs` rewrite the arguments of a call inside the method.

ModLint counts only the last five, the intrusive ones. Whether a given pair actually breaks depends on priorities and on whether they touch the same instruction, which is exactly the part static analysis cannot decide. What it can decide is that two mods rewrite the same method intrusively, and that this is where Mixin conflicts live.

## Step 1: find every Mixin config

A mod points the framework at its Mixins through config JSON files, and the pointer lives in a different place per loader. ModLint collects them from all three:

- Fabric and Quilt: the `mixins` array in `fabric.mod.json`, whose entries are file names or `{"config": ...}` objects.
- Forge and NeoForge: the `MixinConfigs` attribute in the jar manifest, a comma-separated list.
- NeoForge additionally: file-level `[[mixins]]` tables in `neoforge.mods.toml`, each naming a config file.

Each config file names a `package` and lists Mixin class names under `mixins`, `client`, and `server`. ModLint reads all three lists; a client-only overlap is still an overlap.

## Step 2: read the Mixin classes with ASM

For every listed class, ModLint reads the class file out of the jar with ASM's tree API, skipping method bodies and debug info entirely. Nothing is loaded into the JVM and nothing executes; only the annotation structure is read. From each class it takes:

- the target classes, from the `@Mixin` annotation's class list plus its `targets` string list (the string form is how mods target non-public classes);
- for every method carrying one of the five intrusive annotations, the target method name.

For `@Overwrite` the target method name is the Mixin method's own name, since that is how overwrites bind. For the other injectors it is the annotation's `method` selector, reduced to a bare name: descriptors like `render(FJ)V` lose the parenthesized part, and owner-qualified selectors lose everything up to the `;`.

Each (Mixin class, target class, target method, injector) tuple becomes one recorded injection, attributed to the mod that ships it.

## Step 3: overlay the sets

With every mod's injections extracted, the pass groups them by target class and method. Attribution follows the loader's resolution rules: an injection counts for the mod the loader would actually load, so a stale copy of a library that loses resolution cannot create an overlap. Any target hit intrusively by two or more distinct mods becomes one finding that names the target, the mods, and each mod's injector kinds, plus the suggested next step.

Taken verbatim from a real pack's report:

```
POTENTIAL (10)
  [mixin-overlap] ferritecore, lithium
    Intrusive Mixin injections from several mods hit net.minecraft.class_2841#method_12334: ferritecore @Overwrite, lithium @Overwrite. Whichever applies last wins, and the rest may misbehave.
    Fix: Test this pair in game; if they coexist fine, suppress this finding via the ignore file.
```

The `class_2841#method_12334` form is a consequence of how released mods ship: their Mixins are compiled against Minecraft's version-stable intermediary names, not readable development names. That is also what makes the comparison sound: every mod's selectors live in the same namespace, so names from different mods are directly comparable.

## Why this is a heuristic

The detection deliberately trades precision for the ability to run before launch, and it is honest about the direction of each trade:

- Method names are compared without descriptors, so two mods touching different overloads of the same name still match. This over-reports.
- Two `@ModifyConstant` injectors can rewrite different constants in the same method and coexist. Also over-reports.
- Priorities, apply order, and whether two redirects hit the same instruction are runtime facts the scan does not model. A flagged pair may work fine; plenty of popular performance mods overlap on hot vanilla methods and are mutually tested by their authors.
- Malformed configs and unreadable class files are skipped rather than failing the scan, so a broken mod cannot hide the rest of the pack. This under-reports for that one mod.

That is why the finding ships in its own severity tier, `POTENTIAL`, grouped away from the definite findings: high impact if real, not proven by the overlap alone. The suggested workflow is to test the named pair in game and, when it coexists fine, record that fact once in a `.modlintignore` file so the finding stays suppressed in every future run.

On real packs the results are informative rather than noisy: a production 100-mod 1.20.1 server pack reports ten overlaps, all factually true, mostly the expected clusters of performance mods patching the same vanilla hot paths.
