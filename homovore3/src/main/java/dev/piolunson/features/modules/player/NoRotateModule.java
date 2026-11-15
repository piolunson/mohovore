package dev.piolunson.features.modules.player;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.Module;

public class NoRotateModule extends Module {
   public NoRotateModule() {
      super("NoRotate", "Ignores server-forced rotations.", Module.Category.PLAYER);
   }

   public static boolean isActive() {
      if (Homovore.moduleManager == null) {
         return false;
      }

      NoRotateModule module = Homovore.moduleManager.getModuleByClass(NoRotateModule.class);
      return module != null && module.isEnabled();
   }
}
