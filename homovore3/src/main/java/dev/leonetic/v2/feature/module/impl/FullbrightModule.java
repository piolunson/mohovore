package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;

public class FullbrightModule extends Module {
   public final Setting<FullbrightModule.Mode> mode = this.mode("Mode", FullbrightModule.Mode.GAMMA)
      .describe("Gamma flattens the lightmap; Night Vision mimics the potion effect.");

   public FullbrightModule() {
      super("Fullbright", Category.RENDER, "Removes darkness from the world.");
   }

   public enum Mode {
      GAMMA,
      NIGHT_VISION;
   }
}
