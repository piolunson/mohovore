package dev.piolunson.features.modules.player;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.TickEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Bind;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.mixin.client.ClientLevelAccessor;
import dev.piolunson.util.EnchantmentUtil;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.SwapMode;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

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
         if (mc.currentScreen == null) {
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
      return this.pauseInAir.getValue() && !mc.player.isOnGround();
   }

   private void handleThrowBind() {
      if (mc.currentScreen != null) {
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
      } else if (!InventoryUtil.find(Items.EXPERIENCE_BOTTLE, InventoryUtil.FULL_SCOPE).found()) {
         this.throwing = false;
         Command.sendMessage("{red} AutoXP out of XP bottles.");
      } else if (this.shouldThrowNow()) {
         this.throwBottles(6);
      }
   }

   private void throwBottles(int amount) {
      Result xp = InventoryUtil.find(Items.EXPERIENCE_BOTTLE, InventoryUtil.FULL_SCOPE);
      if (xp.found()) {
         float yaw = mc.player.getYaw();
         float pitch = 90.0F;
         Homovore.rotationManager.submit(new RotationRequest("AutoXP", 40, yaw, pitch, RotationRequest.Mode.SILENT));
         mc.interactionManager.syncSelectedSlot();
         Homovore.swapManager.withSwap(xp, SwapMode.ALTSILENT, 0, () -> {
            for (int i = 0; i < amount; i++) {
               PendingUpdateManager handler = ((ClientLevelAccessor)mc.world).homovore$getBlockStatePredictionHandler().incrementSequence();

               try {
                  mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(xp.hand(), handler.getSequence(), yaw, pitch));
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

      for (ItemStack stack : this.gear()) {
         total += this.xpForItem(stack);
      }

      return total;
   }

   private int xpForItem(ItemStack stack) {
      if (!this.isMendable(stack)) {
         return 0;
      }

      int damage = stack.getDamage();
      return damage <= 0 ? 0 : (int)Math.ceil(damage / 2.0);
   }

   private ItemStack[] gear() {
      return new ItemStack[]{
         mc.player.getEquippedStack(EquipmentSlot.HEAD),
         mc.player.getEquippedStack(EquipmentSlot.CHEST),
         mc.player.getEquippedStack(EquipmentSlot.LEGS),
         mc.player.getEquippedStack(EquipmentSlot.FEET),
         mc.player.getMainHandStack(),
         mc.player.getOffHandStack()
      };
   }

   private boolean isMendable(ItemStack stack) {
      return !stack.isEmpty() && stack.getMaxDamage() > 0 && EnchantmentUtil.has(Enchantments.MENDING, stack);
   }
}
