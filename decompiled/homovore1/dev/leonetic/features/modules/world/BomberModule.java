package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.class_1268;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_3965;

public class BomberModule extends Module {
   private final Setting<Float> radius = this.num("Radius", 4.0F, 1.0F, 6.0F);
   private final Setting<Integer> delay = this.num("Delay", 6, 0, 20);
   private final Setting<Boolean> autoDisable = this.bool("AutoDisable", false);
   private int ticksWaited = 0;
   private class_2338 lastPos = null;

   public BomberModule() {
      super("Bomber", "Automatically places and lights TNT around you.", Module.Category.WORLD);
   }

   @Override
   public void onEnable() {
      this.ticksWaited = 0;
      this.lastPos = null;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (this.ticksWaited < this.delay.getValue()) {
            this.ticksWaited++;
         } else {
            this.ticksWaited = 0;
            Result tnt = this.findHotbar(class_1802.field_8626);
            Result fas = this.findHotbar(class_1802.field_8884);
            if (tnt != null && fas != null) {
               this.placeAndIgnite(tnt, fas);
            } else {
               if (this.autoDisable.getValue()) {
                  this.disable();
               }
            }
         }
      }
   }

   private void placeAndIgnite(Result tnt, Result fas) {
      class_2338 player = mc.field_1724.method_24515();
      double r = this.radius.getValue().floatValue();

      for (double x = -r; x <= r; x++) {
         for (double z = -r; z <= r; z++) {
            if (!(x * x + z * z > r * r)) {
               class_2338 pos = player.method_10069((int)x, 0, (int)z);
               if (!pos.equals(this.lastPos) && mc.field_1687.method_8320(pos).method_26215()) {
                  this.lastPos = pos;
                  if (Homovore.placementManager.enqueue(pos, tnt.stack().method_7909())) {
                     Homovore.placementManager.flushQueue();
                     this.igniteAt(pos, fas);
                     return;
                  }
               }
            }
         }
      }
   }

   private void igniteAt(class_2338 pos, Result fas) {
      class_3965 hit = new class_3965(class_243.method_24953(pos), class_2350.field_11036, pos, false);
      Homovore.swapManager.withSwap(fas, SwapMode.ALTSILENT, 150, () -> {
         mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, hit);
         mc.field_1724.method_6104(class_1268.field_5808);
      });
   }

   private Result findHotbar(class_1792 item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() ? r : null;
   }
}
