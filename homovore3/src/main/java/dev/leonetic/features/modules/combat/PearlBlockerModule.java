package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PearlBlockerModule extends Module {
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 0, 0, 44).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 0, 0, 44).setPage("Render");
   private final Map<Integer, BlockPos> tracked = new HashMap<>();
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private static final int MAX_FORWARD_TICKS = 60;
   private static final int SUB_SAMPLES = 8;
   private static final double GRAVITY = 0.03;
   private static final double AIR_DRAG = 0.99;
   private static final double WATER_DRAG = 0.8;

   public PearlBlockerModule() {
      super("PearlBlocker", "Catches enemy pearls by placing obsidian in their flight path.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.tracked.clear();
      this.renderMap.clear();
   }

   @Override
   public void onDisable() {
      this.tracked.clear();
      this.renderMap.clear();
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         Result obs = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
         if (obs.found() && obs.type() != ResultType.OFFHAND) {
            int obsSlot = obs.slot();
            long now = System.currentTimeMillis();

            for (Entity e : mc.world.getEntities()) {
               if (e instanceof EnderPearlEntity pearl
                  && !pearl.isRemoved()
                  && pearl.getOwner() instanceof PlayerEntity owner
                  && owner != mc.player
                  && !Homovore.friendManager.isFriend(owner)
                  && (!pearl.isTouchingWater() || !(pearl.getVelocity().length() < 0.015))) {
                  int id = pearl.getId();
                  BlockPos prev = this.tracked.get(id);
                  if (prev == null || mc.world.getBlockState(prev).isReplaceable()) {
                     BlockPos target = this.findPlaceablePosInPath(pearl);
                     if (target != null && PlaceUtil.canPlace(target)) {
                        if (Homovore.placementManager.enqueue(target, Items.OBSIDIAN)) {
                           this.renderMap.put(target.toImmutable(), now);
                        }

                        this.tracked.put(id, target);
                     }
                  }
               }
            }

            this.tracked.entrySet().removeIf(entry -> {
               Entity found = mc.world.getEntityById(entry.getKey());
               return found == null || found.isRemoved();
            });
            long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
            this.renderMap.entrySet().removeIf(ex -> now - (Long)ex.getValue() > fadeMs);
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               double t = age / fadeMs;
               Color fc = this.fillColor.getValue();
               Color oc = this.outlineColor.getValue();
               RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
               RenderUtil.drawBox(event.getMatrix(), entry.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
            }
         }
      }
   }

   private BlockPos findPlaceablePosInPath(EnderPearlEntity pearl) {
      Vec3d pos = pearl.getEntityPos();
      Vec3d vel = pearl.getVelocity();
      boolean inWater = pearl.isTouchingWater();
      Vec3d eye = mc.player.getEyePos();

      for (int t = 0; t < 60; t++) {
         Vec3d next = pos.add(vel);

         for (int s = 1; s <= 8; s++) {
            Vec3d sample = pos.add(next.subtract(pos).multiply(s / 8.0));
            BlockPos blockPos = BlockPos.ofFloored(sample);
            if (eye.distanceTo(sample) <= 5.1 && mc.world.getBlockState(blockPos).isReplaceable() && PlaceUtil.canPlace(blockPos)) {
               return blockPos;
            }
         }

         pos = next;
         vel = stepVelocity(vel, inWater);
         if (pos.y < mc.world.getBottomY() - 5) {
            break;
         }
      }

      return null;
   }

   private static Vec3d stepVelocity(Vec3d vel, boolean inWater) {
      double drag = inWater ? 0.8 : 0.99;
      return new Vec3d(vel.x * drag, vel.y * drag - 0.03, vel.z * drag);
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
   }
}
