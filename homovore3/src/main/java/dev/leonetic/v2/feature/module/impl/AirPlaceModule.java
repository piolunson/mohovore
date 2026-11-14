package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.UseItemEvent;
import dev.leonetic.v2.event.system.Subscribe;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.PlacementManager;
import dev.leonetic.v2.render.BlockRender;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import dev.leonetic.v2.util.world.PlacementWorldSnapshot;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;

public final class AirPlaceModule extends Module {
   public final Setting<Boolean> customRange = this.bool("CustomRange", true).describe("Reaches past the server's interaction range instead of matching it.");
   public final Setting<Double> range = this.number("Range", (double)3.5, (double)1.0, (double)6.0)
      .visibleWhen(this.customRange::getValue)
      .describe("How far from the eyes the air target may sit.");
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.05, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> sideColor = this.color("SideColor", 0, 0, 0, 0).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 200).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)4.0, (double)0.5, (double)5.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(() -> this.render.getValue()).page("Render");
   private final Map<BlockPos, Long> renderMap = new HashMap<>();

   public AirPlaceModule() {
      super("AirPlace", Category.EXTRA, "Places the block you are holding in mid-air where you are looking.");
   }

   @Override
   public void onDisable() {
      this.renderMap.clear();
   }

   private BlockPos target() {
      if (mc.player != null && mc.world != null && !mc.player.isSpectator() && NetworkThreadExecutor.isConnected()) {
         if (mc.crosshairTarget != null && mc.crosshairTarget.getType() != Type.MISS) {
            return null;
         } else if (this.heldBlock() == null) {
            return null;
         } else {
            Entity camera = mc.getCameraEntity();
            if (camera == null) {
               return null;
            } else {
               double reach = this.customRange.getValue() ? this.range.getValue() : mc.player.getBlockInteractionRange();
               if (!(camera.raycast(reach, 0.0F, false) instanceof BlockHitResult hit)) {
                  return null;
               } else {
                  BlockPos var7 = hit.getBlockPos().toImmutable();
                  PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
                  return world.available() && world.known(var7) && world.replaceable(var7) && !world.selfCollision(var7) && !world.entityCollision(var7)
                     ? var7
                     : null;
               }
            }
         }
      } else {
         return null;
      }
   }

   @Subscribe
   public void onUseItem(UseItemEvent event) {
      if (mc.player != null && !mc.player.isRiding()) {
         BlockPos pos = this.target();
         ItemStack held = this.heldBlock();
         if (pos != null && held != null) {
            Homovore2Client.placements().enqueue(pos, null, held.getItem(), new PlacementManager.Options(true, false, this.customRange.getValue(), true));
            event.cancel();
         }
      }
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (!this.render.getValue()) {
         this.renderMap.clear();
      } else {
         long now = System.currentTimeMillis();
         BlockPos pos = this.target();
         if (pos != null) {
            this.renderMap.put(pos, now);
         }

         this.renderMap
            .entrySet()
            .removeIf(
               entry -> {
                  if (BlockRender.expired(entry.getValue(), now, this.fadeTime.getValue())) {
                     return true;
                  }

                  BlockRender.cuboid(
                     entry.getKey(),
                     this.lineColor.getValue(),
                     this.sideColor.getValue(),
                     this.lineWidth.getValue().floatValue(),
                     BlockRender.alpha(entry.getValue(), now, this.fadeTime.getValue()),
                     this.throughWalls.getValue()
                  );
                  return false;
               }
            );
      }
   }

   private ItemStack heldBlock() {
      if (mc.player == null) {
         return null;
      }

      ItemStack held = mc.player.getMainHandStack();
      return held.getItem() instanceof BlockItem ? held : null;
   }
}
