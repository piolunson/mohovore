# Build

Target: Minecraft 1.21.11, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Yarn 1.21.11+build.6, Java 21, Gradle 9.9.1, Loom 1.19.4.

```bash
cd homovore3
JAVA_HOME=/path/to/jdk-25 gradle build
```

Loom 1.19.4 needs a JVM 25 to run Gradle; the sources compile with `--release 21`.

Result of the last run: **BUILD SUCCESSFUL**, artifact at `homovore3/dist/homovore-3-1.0.2.jar`.

Notes:

- The access widener is in the `named` namespace, as required by Loom 1.19.
- Decompiler artifacts were repaired to make the sources compile: `(X)(Object)this` casts in mixins, `(Object)this ==` comparisons, generic casts (`EnumConverter`, `EnumArgumentType`, `MultiListSetting`), mixin accessor casts (`OptionInstanceAccessor`, `RenderSetupAccessor`) and the missing `switch` lambda in `BaritoneBridge`.
- 1.21.11 has two `HeldItemRenderer.renderItem` overloads, so every mixin annotation that targets it now carries an explicit descriptor. The same was done for the other overloaded targets (`renderLabelIfPresent`, `getRotationVector`, `addParticle`, `renderScoreboardSidebar`).
- The 1.0.1 jar has not been re-tested in a running client; 1.0.0 crashed at startup on `MixinItemInHandRenderer`.
