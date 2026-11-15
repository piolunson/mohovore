package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.module.ModuleManager;
import dev.piolunson.v2.feature.setting.Bind;
import dev.piolunson.v2.feature.setting.Setting;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

public final class BetterCamModule extends Module {
   private static final double MAX_DISTANCE = 32.0;
   private static final double MIN_SCROLL_DISTANCE = 0.1;
   public final Setting<Boolean> clip = this.bool("Clip", true).describe("Lets the camera pass through blocks instead of being pulled in by them.");
   public final Setting<Double> distance = this.number("Distance", (double)4.0, (double)0.0, (double)32.0)
      .describe("How far behind the player the third person camera sits.")
      .onChange(this::resetDistance);
   public final Setting<Boolean> scrolling = this.bool("Scrolling", true).page("Scrolling").describe("Changes the camera distance with the scroll wheel.");
   public final Setting<Bind> scrollBind = this.register(new Setting<>("ScrollBind", new Bind(342)))
      .page("Scrolling")
      .describe("Held for the wheel to move the camera. Unbound means the wheel always does.");
   public final Setting<Double> sensitivity = this.number("Sensitivity", (double)1.0, (double)0.01, (double)5.0)
      .page("Scrolling")
      .describe("How much of the current distance one notch of the wheel adds or removes.");
   private double current = 4.0;
   private Perspective lastCameraType;

   public BetterCamModule() {
      super("BetterCam", Category.RENDER, "Controls the third person camera's distance and block clipping.");
      this.scrollBind.visibleWhen(this.scrolling::getValue);
      this.sensitivity.visibleWhen(this.scrolling::getValue);
   }

   public static BetterCamModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(BetterCamModule.class);
   }

   public static BetterCamModule active() {
      BetterCamModule module = get();
      return module != null && module.isEnabled() ? module : null;
   }

   @Override
   public void onEnable() {
      this.current = this.distance.getValue();
      this.lastCameraType = mc.options == null ? null : mc.options.getPerspective();
   }

   @Override
   public void onTick() {
      if (mc.options != null) {
         Perspective type = mc.options.getPerspective();
         if (type != this.lastCameraType) {
            this.lastCameraType = type;
            this.current = this.distance.getValue();
         }
      }
   }

   private void resetDistance() {
      this.current = this.distance.getValue();
   }

   public float distance() {
      return (float)this.current;
   }

   public boolean clips() {
      return this.clip.getValue();
   }

   public boolean onScroll(double amount) {
      if (this.scrolling.getValue() && amount != 0.0) {
         if (mc.options == null || mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            return false;
         }

         if (mc.currentScreen != null) {
            return false;
         }

         Bind bind = this.scrollBind.getValue();
         if (!bind.isEmpty() && !bind.isDown()) {
            return false;
         }

         double base = Math.max(this.current, 0.1);
         this.current = MathHelper.clamp(base - amount * 0.25 * this.sensitivity.getValue() * base, 0.1, 32.0);
         return true;
      } else {
         return false;
      }
   }
}
