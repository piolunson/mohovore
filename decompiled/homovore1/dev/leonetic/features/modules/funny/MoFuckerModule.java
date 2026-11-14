package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_243;

public class MoFuckerModule extends Module {
   private final Setting<Float> range = this.num("Range", 5.0F, 1.0F, 6.0F);
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20);
   private static final int PRIORITY = 100;
   private MoFuckerModule.State state;
   private class_2338 targetPos;
   private int ticks;

   public MoFuckerModule() {
      super("MoFucker", "On keybind, dumps a water bucket on the nearest enemy and scoops it back up.", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      this.state = MoFuckerModule.State.PLACE;
      this.targetPos = null;
      this.ticks = 0;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck() && mc.field_1755 == null) {
         switch (this.state) {
            case PLACE:
               this.place();
               break;
            case COLLECT:
               this.collect();
         }
      } else {
         this.disable();
      }
   }

   private void place() {
      class_1657 target = this.findTarget();
      if (target == null) {
         this.disable();
      } else {
         Result water = InventoryUtil.find(class_1802.field_8705, InventoryUtil.FULL_SCOPE);
         if (!water.found()) {
            this.disable();
         } else {
            this.targetPos = target.method_24515();
            this.useAt(class_243.method_24955(this.targetPos), water, "MoFucker_place");
            this.state = MoFuckerModule.State.COLLECT;
            this.ticks = 0;
         }
      }
   }

   private void collect() {
      if (this.ticks++ >= this.delay.getValue()) {
         Result bucket = InventoryUtil.find(class_1802.field_8550, InventoryUtil.FULL_SCOPE);
         if (!bucket.found()) {
            this.disable();
         } else {
            this.useAt(class_243.method_24953(this.targetPos), bucket, "MoFucker_collect");
            this.disable();
         }
      }
   }

   private void useAt(class_243 aim, Result item, String id) {
      float[] angles = MathUtil.calcAngle(mc.field_1724.method_5836(1.0F), aim);
      Homovore.rotationManager.submit(new RotationRequest("MoFucker", 100, angles[0], angles[1], RotationRequest.Mode.SILENT));
      Homovore.swapManager.withSwap(item, SwapMode.ALTSILENT, 150, () -> {
         float prevYaw = mc.field_1724.method_36454();
         float prevPitch = mc.field_1724.method_36455();
         mc.field_1724.method_36456(angles[0]);
         mc.field_1724.method_36457(angles[1]);

         try {
            mc.field_1761.method_2919(mc.field_1724, item.hand());
         } finally {
            mc.field_1724.method_36456(prevYaw);
            mc.field_1724.method_36457(prevPitch);
         }
      });
   }

   private class_1657 findTarget() {
      double reachSq = this.range.getValue() * this.range.getValue();
      class_1657 best = null;
      double bestSq = Double.MAX_VALUE;

      for (class_1657 p : mc.field_1687.method_18456()) {
         if (p != mc.field_1724 && !Homovore.friendManager.isFriend(p)) {
            double distSq = mc.field_1724.method_5858(p);
            if (!(distSq > reachSq) && distSq < bestSq) {
               bestSq = distSq;
               best = p;
            }
         }
      }

      return best;
   }

   private enum State {
      PLACE,
      COLLECT;
   }
}
