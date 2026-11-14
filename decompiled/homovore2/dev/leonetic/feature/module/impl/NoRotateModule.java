package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;

public final class NoRotateModule extends Module {
   public NoRotateModule() {
      super("NoRotate", Category.MOVEMENT, "Ignores server-forced rotations.");
   }

   public static NoRotateModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(NoRotateModule.class);
   }

   public static boolean isActive() {
      NoRotateModule module = get();
      return module != null && module.isEnabled();
   }
}
