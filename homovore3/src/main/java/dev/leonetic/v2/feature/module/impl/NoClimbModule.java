package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.module.ModuleManager;

public final class NoClimbModule extends Module {
   public NoClimbModule() {
      super("NoClimb", Category.MOVEMENT, "Stops scaffolding from pulling you upwards on contact.");
   }

   public static NoClimbModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(NoClimbModule.class);
   }

   public static boolean isActive() {
      NoClimbModule module = get();
      return module != null && module.isEnabled();
   }
}
