package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;

public final class NoJumpDelayModule extends Module {
   public NoJumpDelayModule() {
      super("NoJumpDelay", Category.MOVEMENT, "Lets you jump again the instant you land.");
   }

   public static NoJumpDelayModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(NoJumpDelayModule.class);
   }

   public static boolean isActive() {
      NoJumpDelayModule module = get();
      return module != null && module.isEnabled();
   }
}
