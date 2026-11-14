package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import java.util.function.Predicate;

public class ViewModelModule extends Module {
   public final Setting<Double> scale = this.num("Scale", 1.0, 0.0, 3.0);
   public final Setting<Double> posX = this.num("PosX", 0.0, -1.0, 1.0);
   public final Setting<Double> posY = this.num("PosY", 0.0, -1.0, 1.0);
   public final Setting<Double> posZ = this.num("PosZ", 0.0, -1.0, 1.0);
   public final Setting<Boolean> noSway = this.bool("NoSway", false);
   public final Setting<Boolean> noSwapAnimation = this.bool("NoSwapAnimation", false);
   public final Setting<Boolean> oldAnimation = this.bool("OldAnimation", false);

   public ViewModelModule() {
      super("ViewModel", "change scale position and stuff", Module.Category.RENDER);
   }

   public static ViewModelModule getInstance() {
      return Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(ViewModelModule.class);
   }

   public static boolean isActive(Predicate<ViewModelModule> predicate) {
      ViewModelModule module = getInstance();
      return module != null && module.isEnabled() && predicate.test(module);
   }
}
