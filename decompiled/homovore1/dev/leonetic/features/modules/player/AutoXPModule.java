package dev.leonetic.features.modules.player;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Bind;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.EnchantmentUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1893;
import net.minecraft.class_2886;
import net.minecraft.class_7202;

public class AutoXPModule extends Module {
   private final Setting<Bind> throwBind = this.key("Throw", Bind.none());
   private final Setting<Boolean> pauseInAir = this.bool("PauseInAir", true);
   private final Setting<Boolean> autoRepair = this.bool("AutoRepair", true);
   private static final int THROWS_PER_BATCH = 6;
   private static final long BATCH_INTERVAL_MS = 300L;
   private boolean throwing;
   private boolean keyWasDown;
   private long lastThrowMs;

   public AutoXPModule() {
      super("AutoXP", "Throws XP bottles to mend your armor and tools.", Module.Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.throwing = false;
      this.keyWasDown = false;
      this.lastThrowMs = 0L;
   }

   @Override
   public String getDisplayInfo() {
      if (!this.throwing) {
         return null;
      } else {
         return !nullCheck() && this.isPaused() ? "Paused" : this.xpNeeded() + " XP";
      }
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         this.handleThrowBind();
         if (mc.field_1755 == null) {
            if (this.throwing) {
               if (!this.isPaused()) {
                  this.tickThrow();
               }
            } else if (this.autoRepair.getValue()) {
               if (this.xpNeeded() > 0) {
                  if (!this.isPaused()) {
                     if (this.shouldThrowNow()) {
                        this.throwBottles(6);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isPaused() {
      return this.pauseInAir.getValue() && !mc.field_1724.method_24828();
   }

   private void handleThrowBind() {
      if (mc.field_1755 != null) {
         this.keyWasDown = false;
      } else {
         boolean down = !this.throwBind.getValue().isEmpty() && this.throwBind.getValue().isDown();
         if (down && !this.keyWasDown) {
            this.throwing = !this.throwing;
            if (this.throwing) {
               this.lastThrowMs = 0L;
               Command.sendMessage("{green} AutoXP mending, " + this.xpNeeded() + " XP required.");
            } else {
               Command.sendMessage("{red} AutoXP throw disabled.");
            }
         }

         this.keyWasDown = down;
      }
   }

   private void tickThrow() {
      if (this.xpNeeded() <= 0) {
         this.throwing = false;
         Command.sendMessage("{green} AutoXP mending complete.");
      } else if (!InventoryUtil.find(class_1802.field_8287, InventoryUtil.FULL_SCOPE).found()) {
         this.throwing = false;
         Command.sendMessage("{red} AutoXP out of XP bottles.");
      } else if (this.shouldThrowNow()) {
         this.throwBottles(6);
      }
   }

   private void throwBottles(int amount) {
      Result xp = InventoryUtil.find(class_1802.field_8287, InventoryUtil.FULL_SCOPE);
      if (xp.found()) {
         float yaw = mc.field_1724.method_36454();
         float pitch = 90.0F;
         Homovore.rotationManager.submit(new RotationRequest("AutoXP", 40, yaw, pitch, RotationRequest.Mode.SILENT));
         mc.field_1761.method_2911();
         Homovore.swapManager.withSwap(xp, SwapMode.ALTSILENT, 0, () -> {
            for (int i = 0; i < amount; i++) {
               class_7202 handler = ((ClientLevelAccessor)mc.field_1687).homovore$getBlockStatePredictionHandler().method_41937();

               try {
                  mc.method_1562().method_52787(new class_2886(xp.hand(), handler.method_41942(), yaw, pitch));
               } catch (Throwable t$) {
                  if (handler != null) {
                     try {
                        handler.close();
                     } catch (Throwable x2) {
                        t$.addSuppressed(x2);
                     }
                  }

                  throw t$;
               }

               if (handler != null) {
                  handler.close();
               }
            }
         });
      }
   }

   private boolean shouldThrowNow() {
      long now = System.currentTimeMillis();
      if (now - this.lastThrowMs < 300L) {
         return false;
      }

      this.lastThrowMs = now;
      return true;
   }

   private int xpNeeded() {
      int total = 0;

      for (class_1799 stack : this.gear()) {
         total += this.xpForItem(stack);
      }

      return total;
   }

   private int xpForItem(class_1799 stack) {
      if (!this.isMendable(stack)) {
         return 0;
      }

      int damage = stack.method_7919();
      return damage <= 0 ? 0 : (int)Math.ceil(damage / 2.0);
   }

   private class_1799[] gear() {
      return new class_1799[]{
         mc.field_1724.method_6118(class_1304.field_6169),
         mc.field_1724.method_6118(class_1304.field_6174),
         mc.field_1724.method_6118(class_1304.field_6172),
         mc.field_1724.method_6118(class_1304.field_6166),
         mc.field_1724.method_6047(),
         mc.field_1724.method_6079()
      };
   }

   private boolean isMendable(class_1799 stack) {
      return !stack.method_7960() && stack.method_7936() > 0 && EnchantmentUtil.has(class_1893.field_9101, stack);
   }
}
