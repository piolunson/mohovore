package dev.piolunson.features.modules.world;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.SwapMode;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BomberModule extends Module {
   private final Setting<Float> radius = this.num("Radius", 4.0F, 1.0F, 6.0F);
   private final Setting<Integer> delay = this.num("Delay", 6, 0, 20);
   private final Setting<Boolean> autoDisable = this.bool("AutoDisable", false);
   private int ticksWaited = 0;
   private BlockPos lastPos = null;

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
            Result tnt = this.findHotbar(Items.TNT);
            Result fas = this.findHotbar(Items.FLINT_AND_STEEL);
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
      BlockPos player = mc.player.getBlockPos();
      double r = this.radius.getValue().floatValue();

      for (double x = -r; x <= r; x++) {
         for (double z = -r; z <= r; z++) {
            if (!(x * x + z * z > r * r)) {
               BlockPos pos = player.add((int)x, 0, (int)z);
               if (!pos.equals(this.lastPos) && mc.world.getBlockState(pos).isAir()) {
                  this.lastPos = pos;
                  if (Homovore.placementManager.enqueue(pos, tnt.stack().getItem())) {
                     Homovore.placementManager.flushQueue();
                     this.igniteAt(pos, fas);
                     return;
                  }
               }
            }
         }
      }
   }

   private void igniteAt(BlockPos pos, Result fas) {
      BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
      Homovore.swapManager.withSwap(fas, SwapMode.ALTSILENT, 150, () -> {
         mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
         mc.player.swingHand(Hand.MAIN_HAND);
      });
   }

   private Result findHotbar(Item item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() ? r : null;
   }
}
