# mohovore

Decompiled, deobfuscated and rebuilt sources of the **Homovore** Minecraft mods, plus a merged
build called **Homovore 3**.

- Upstream inputs: `homovore-72a4151c-1.2.1.jar` (Homovore 1) and `homovore-2-1.0.0_(3).jar`
  (Homovore 2), both kept in `originals/`.
- Target platform: Minecraft 1.21.11, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Java 21.

## Download

| Artifact | Where |
| --- | --- |
| Homovore 3 build | [release v1.0.2](https://github.com/piolunson/mohovore/releases/tag/v1.0.2), also `homovore3/dist/homovore-3-1.0.2.jar` |
| SHA-256 | `6dfa171a338c0791895a6a81323a63f66e26810f3835dcccf8ad6032d1f496e5` |

Drop the jar into `.minecraft/mods` together with Fabric Loader 0.20.1 and Fabric API
0.141.6+1.21.11. The jar compiles cleanly; it has not been play-tested beyond reaching client
startup on 1.21.11.

## Repository layout

```
originals/          the two upstream jars, unmodified
remapped/           the same jars after tiny-remapper (official -> intermediary -> named)
decompiled/         first decompilation pass, obfuscated names (kept for reference)
decompiled-named/   decompilation of the remapped jars: readable Yarn names
decompile.log       shortened log of the decompilation run
homovore3/          merged, buildable Gradle project + dist/ jar
```

`decompiled-named/homovore1` (252 `.java` files) and `decompiled-named/homovore2` (285 files)
are the source of truth for `homovore3`. Neither contains `class_`, `method_` or `field_`
remnants.

## How the sources were produced

1. **Decompile** the original jars with Vineflower 1.12.0 on Temurin JDK 21. One class
   (`BaritoneBridge`) crashes Vineflower, so CFR 0.152 was used for it.
2. **Remap the bytecode** with tiny-remapper 0.15.0 using Yarn 1.21.11+build.6
   (`official -> intermediary -> named`). The 1.21.11 client remapped to intermediary is passed
   as classpath; without it member references are left unmapped.
3. **Decompile again** and run a text pass over `@Inject`/`@Shadow` targets and nested classes.
4. **Merge** into `homovore3` and make it compile (see below).

## Homovore 3 merge rules

1. **Base is Homovore 1.** The whole h1 framework (event bus, `Setting`, GUI, managers, mixins,
   modules) is preserved unchanged in package `dev.leonetic`.
2. **Duplicates come from Homovore 1.** An h2 module that duplicates an h1 feature is *not
   registered* in the h2 registry, but its class stays because the h2 framework references it
   statically.
3. **PistonCrystal comes from Homovore 2.** The h1 `PistonCrystalModule` was deleted along with
   its import and registration; the active one is
   `dev.leonetic.v2.feature.module.impl.PistonCrystalModule`.
4. **All h2 code lives in `dev.leonetic.v2`.** Both clients use the same `dev.leonetic.mixin`
   package, so the rename avoids class and mixin collisions.

Disabled h2 modules with an exact h1 counterpart: AutoLog, AutoMine, AutoTotem, AutoTrap, AutoXp,
BreakIndicators, ClickGui, FastUse, Fullbright, Hud, InstantRekit, LogoutSpots, Nametags, NoRender,
NoRotate, Notifications, PearlBlocker, Phase, Scaffold, ShulkerPreview, Sprint, SwordGap, Tablist,
Velocity, ViewModel. Same feature under another name (`AnchorPlacer` -> `AutoAnchor`,
`CrystalPlacer` -> `AutoCrystal`, `MiddleClick` -> `MiddleClickExtra`). Close but distinct
features stay enabled from h2: Chams, KillAura, Tracers, Trails, Bounce, Fixes, Freecam and the
rest listed in `homovore3/README.md`.

## Building

```bash
cd homovore3
JAVA_HOME=/path/to/jdk-25 gradle build
```

Gradle 9.9.1 with Loom 1.19.4 (Loom needs Gradle >= 9.8 and a JVM 25 to run; sources compile with
`--release 21`). The access widener is in the `named` namespace, which Loom 1.19 requires.

## Decompiler repairs

Making the decompiled sources compile required these edits, all limited to artefacts of
decompilation: `(X)(Object)this` casts and `(Object)this ==` comparisons in mixins, generic casts
(`EnumConverter`, `EnumArgumentType`, `MultiListSetting`, `registerNumber`), mixin accessor casts
(`OptionInstanceAccessor`, `RenderSetupAccessor`), library dependencies taken from
`META-INF/jars` (jlayer, discord-ipc, catformat) and the missing `switch` lambda in
`BaritoneBridge`. Behaviour was not intentionally changed.

Mixin annotations needed a second pass as well. The remap step rewrote method names in
`@Inject`/`@At` strings but dropped their descriptors, which makes Mixin pick an arbitrary
overload when the target method is overloaded. v1.0.2 restores explicit descriptors for every
ambiguous target (`HeldItemRenderer.renderItem`, `PlayerEntityRenderer.renderLabelIfPresent`,
`Entity.getRotationVector`, `ParticleManager.addParticle`, `InGameHud.renderScoreboardSidebar`).

## Credits and licence

The code is the work of the original Homovore authors (Leonetic, widerthaneurasia); the mod
manifest declares AGPL-3.0-or-later. This repository only decompiles, deobfuscates and rebuilds
it for interoperability and study.
