package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.setting.Setting;
import java.util.function.Predicate;

public class ViewModelModule extends Module {
   public final Setting<Double> scale = this.number("Scale", (double)1.0, (double)0.0, (double)3.0)
      .describe("Size of the held item, scaled about its resting position.");
   public final Setting<Double> posX = this.number("PosX", (double)0.0, (double)-1.0, (double)1.0)
      .describe("Sideways offset, mirrored for the off hand so both hands move outward.");
   public final Setting<Double> posY = this.number("PosY", (double)0.0, (double)-1.0, (double)1.0).describe("Vertical offset of the held item.");
   public final Setting<Double> posZ = this.number("PosZ", (double)0.0, (double)-1.0, (double)1.0)
      .describe("Depth offset: negative pushes the item away from the camera.");
   public final Setting<Boolean> noSway = this.bool("NoSway", true).describe("Pins the hand to the current look angle so it stops lagging behind turns.");
   public final Setting<Boolean> noSwapAnimation = this.bool("NoSwapAnimation", true)
      .describe("Swaps items instantly instead of lowering and raising the hand.");
   public final Setting<Boolean> oldAnimation = this.bool("OldAnimation", true).describe("Holds the hand fully raised, as it was before the equip animation.");
   public final Setting<Double> swingSpeed = this.number("SwingSpeed", (double)1.0, (double)0.1, (double)5.0)
      .page("Swing")
      .describe("Multiplies how fast a swing plays out. Visual only.");
   public final Setting<Integer> swingDelay = this.integer("SwingDelay", 1, 0, 20)
      .page("Swing")
      .describe("Ticks a swing must run before another one may restart it.");

   public ViewModelModule() {
      super("ViewModel", Category.RENDER, "Changes the scale, position and animation of the held item.");
   }

   public static ViewModelModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(ViewModelModule.class);
   }

   public static ViewModelModule active() {
      ViewModelModule module = get();
      return module != null && module.isEnabled() ? module : null;
   }

   public static boolean isActive(Predicate<ViewModelModule> predicate) {
      ViewModelModule module = active();
      return module != null && predicate.test(module);
   }
}
