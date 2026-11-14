package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.RotationManager;
import dev.leonetic.v2.util.InteractionUtil;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

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
      boolean focused = mc.isWindowFocused();
      ClientPlayerEntity player = mc.player;
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
            && (Math.abs(MathHelper.wrapDegrees(player.getYaw() - this.expectedYaw)) > 0.01F || Math.abs(player.getPitch() - this.expectedPitch) > 0.01F);
         boolean mouseMoved = !Double.isNaN(this.mouseX) && (mc.mouse.getX() != this.mouseX || mc.mouse.getY() != this.mouseY);
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
            player.swingHand(Hand.MAIN_HAND, false);
            if (NetworkThreadExecutor.isConnected()) {
               InteractionUtil.swing(Hand.MAIN_HAND);
            }
         }

         if (this.afk && this.spin.getValue() && NetworkThreadExecutor.isConnected()) {
            if (!this.spinning) {
               this.spinYaw = Homovore2Client.rotations().serverRotation().yaw();
               this.spinning = true;
            }

            this.spinYaw = MathHelper.wrapDegrees(this.spinYaw + 6.0F);
            Homovore2Client.rotations().motion(new RotationManager.Angles(this.spinYaw, player.getPitch()), Integer.MIN_VALUE, null);
         } else {
            this.spinning = false;
         }

         this.expectedYaw = player.getYaw();
         this.expectedPitch = player.getPitch();
         this.observingPlayer = true;
         this.wasFocused = focused;
         this.rememberMouse();
      }
   }

   private boolean hasGameplayInput() {
      if (!mc.options.forwardKey.isPressed()
         && !mc.options.backKey.isPressed()
         && !mc.options.leftKey.isPressed()
         && !mc.options.rightKey.isPressed()
         && !mc.options.jumpKey.isPressed()
         && !mc.options.sneakKey.isPressed()
         && !mc.options.sprintKey.isPressed()
         && !mc.options.attackKey.isPressed()
         && !mc.options.useKey.isPressed()
         && !mc.options.pickItemKey.isPressed()
         && !mc.options.dropKey.isPressed()
         && !mc.options.swapHandsKey.isPressed()) {
         for (KeyBinding hotbarKey : mc.options.hotbarKeys) {
            if (hotbarKey.isPressed()) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private void rememberMouse() {
      this.mouseX = mc.mouse.getX();
      this.mouseY = mc.mouse.getY();
   }
}
