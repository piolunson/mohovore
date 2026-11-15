package dev.piolunson.features.modules.render;

import dev.piolunson.features.modules.Module;
import dev.piolunson.mixin.client.OptionInstanceAccessor;

public class FullbrightModule extends Module {
   private double previousGamma;

   public FullbrightModule() {
      super("Fullbright", "Sets gamma to maximum for full brightness", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.previousGamma = (Double)mc.options.getGamma().getValue();
      this.setGamma(16.0);
   }

   @Override
   public void onDisable() {
      this.setGamma(this.previousGamma);
   }

   @Override
   public void onTick() {
      this.setGamma(16.0);
   }

   private void setGamma(double value) {
      ((OptionInstanceAccessor)(Object)mc.options.getGamma()).homovore$setValue(value);
   }
}
