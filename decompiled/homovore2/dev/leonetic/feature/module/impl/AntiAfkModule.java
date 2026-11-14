package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.InteractionUtil;
import dev.leonetic.util.network.NetworkThreadExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_1268;
import net.minecraft.class_304;
import net.minecraft.class_3532;
import net.minecraft.class_746;

public final class AntiAfkModule extends Module {
   private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(1L);
   private static final long SWING_NANOS = TimeUnit.SECONDS.toNanos(10L);
   private static final float SPIN_DEGREES_PER_TICK = 6.0F;
   private static final float ROTATION_EPSILON = 0.01F;
   private static final int ROTATION_PRIORITY = Integer.MIN_VALUE;
   public final Setting<Boolean> spin = this.bool("Spin", true).describe("Spin your player while Anti Afk is active.");
   private long lastActivityNanos;
   private long lastSwingNanos;
   private boolean afk;
   private boolean wasFocused;
   private boolean observingPlayer;
   private boolean spinning;
   private float expectedYaw;
   private float expectedPitch;
   private float spinYaw;
   private double mouseX;
   private double mouseY;

   public AntiAfkModule() {
      super("AntiAfk", Category.SAFETY, "Swings periodically after focus is lost or you are idle for one minute.");
   }

   @Override
   public void onEnable() {
      long now = System.nanoTime();
      this.lastActivityNanos = now;
      this.lastSwingNanos = now;
      this.afk = false;
      this.wasFocused = true;
      this.observingPlayer = false;
      this.spinning = false;
      this.mouseX = Double.NaN;
      this.mouseY = Double.NaN;
   }

   @Override
   public void onDisable() {
      this.afk = false;
      this.observingPlayer = false;
      this.spinning = false;
   }

   @Override
   public void onTick() {
      long now = System.nanoTime();
      boolean focused = mc.method_1569();
      class_746 player = mc.field_1724;
      if (player == null) {
         this.lastActivityNanos = now;
         this.lastSwingNanos = now;
         this.afk = false;
         this.wasFocused = focused;
         this.observingPlayer = false;
         this.spinning = false;
         this.rememberMouse();
      } else {
         boolean focusRegained = focused && !this.wasFocused;
         boolean cameraMoved = this.observingPlayer
            && (
               Math.abs(class_3532.method_15393(player.method_36454() - this.expectedYaw)) > 0.01F
                  || Math.abs(player.method_36455() - this.expectedPitch) > 0.01F
            );
         boolean mouseMoved = !Double.isNaN(this.mouseX) && (mc.field_1729.method_1603() != this.mouseX || mc.field_1729.method_1604() != this.mouseY);
         if (focused && (focusRegained || cameraMoved || mouseMoved || this.hasGameplayInput())) {
            this.lastActivityNanos = now;
         }

         boolean nowAfk = !focused || now - this.lastActivityNanos >= IDLE_NANOS;
         if (nowAfk && !this.afk) {
            this.lastSwingNanos = now - SWING_NANOS;
         }

         this.afk = nowAfk;
         if (this.afk && now - this.lastSwingNanos >= SWING_NANOS) {
            this.lastSwingNanos = now;
            player.method_23667(class_1268.field_5808, false);
            if (NetworkThreadExecutor.isConnected()) {
               InteractionUtil.swing(class_1268.field_5808);
            }
         }

         if (this.afk && this.spin.getValue() && NetworkThreadExecutor.isConnected()) {
            if (!this.spinning) {
               this.spinYaw = Homovore2Client.rotations().serverRotation().yaw();
               this.spinning = true;
            }

            this.spinYaw = class_3532.method_15393(this.spinYaw + 6.0F);
            Homovore2Client.rotations().motion(new RotationManager.Angles(this.spinYaw, player.method_36455()), Integer.MIN_VALUE, null);
         } else {
            this.spinning = false;
         }

         this.expectedYaw = player.method_36454();
         this.expectedPitch = player.method_36455();
         this.observingPlayer = true;
         this.wasFocused = focused;
         this.rememberMouse();
      }
   }

   private boolean hasGameplayInput() {
      if (!mc.field_1690.field_1894.method_1434()
         && !mc.field_1690.field_1881.method_1434()
         && !mc.field_1690.field_1913.method_1434()
         && !mc.field_1690.field_1849.method_1434()
         && !mc.field_1690.field_1903.method_1434()
         && !mc.field_1690.field_1832.method_1434()
         && !mc.field_1690.field_1867.method_1434()
         && !mc.field_1690.field_1886.method_1434()
         && !mc.field_1690.field_1904.method_1434()
         && !mc.field_1690.field_1871.method_1434()
         && !mc.field_1690.field_1869.method_1434()
         && !mc.field_1690.field_1831.method_1434()) {
         for (class_304 hotbarKey : mc.field_1690.field_1852) {
            if (hotbarKey.method_1434()) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private void rememberMouse() {
      this.mouseX = mc.field_1729.method_1603();
      this.mouseY = mc.field_1729.method_1604();
   }
}
