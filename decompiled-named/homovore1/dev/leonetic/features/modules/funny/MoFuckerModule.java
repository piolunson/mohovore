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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MoFuckerModule extends Module {
   private final Setting<Float> range = this.num("Range", 5.0F, 1.0F, 6.0F);
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20);
   private static final int PRIORITY = 100;
   private MoFuckerModule.State state;
   private BlockPos targetPos;
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
      if (!nullCheck() && mc.currentScreen == null) {
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
      PlayerEntity target = this.findTarget();
      if (target == null) {
         this.disable();
      } else {
         Result water = InventoryUtil.find(Items.WATER_BUCKET, InventoryUtil.FULL_SCOPE);
         if (!water.found()) {
            this.disable();
         } else {
            this.targetPos = target.getBlockPos();
            this.useAt(Vec3d.ofBottomCenter(this.targetPos), water, "MoFucker_place");
            this.state = MoFuckerModule.State.COLLECT;
            this.ticks = 0;
         }
      }
   }

   private void collect() {
      if (this.ticks++ >= this.delay.getValue()) {
         Result bucket = InventoryUtil.find(Items.BUCKET, InventoryUtil.FULL_SCOPE);
         if (!bucket.found()) {
            this.disable();
         } else {
            this.useAt(Vec3d.ofCenter(this.targetPos), bucket, "MoFucker_collect");
            this.disable();
         }
      }
   }

   private void useAt(Vec3d aim, Result item, String id) {
      float[] angles = MathUtil.calcAngle(mc.player.getCameraPosVec(1.0F), aim);
      Homovore.rotationManager.submit(new RotationRequest("MoFucker", 100, angles[0], angles[1], RotationRequest.Mode.SILENT));
      Homovore.swapManager.withSwap(item, SwapMode.ALTSILENT, 150, () -> {
         float prevYaw = mc.player.getYaw();
         float prevPitch = mc.player.getPitch();
         mc.player.setYaw(angles[0]);
         mc.player.setPitch(angles[1]);

         try {
            mc.interactionManager.interactItem(mc.player, item.hand());
         } finally {
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
         }
      });
   }

   private PlayerEntity findTarget() {
      double reachSq = this.range.getValue() * this.range.getValue();
      PlayerEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (PlayerEntity p : mc.world.getPlayers()) {
         if (p != mc.player && !Homovore.friendManager.isFriend(p)) {
            double distSq = mc.player.squaredDistanceTo(p);
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
