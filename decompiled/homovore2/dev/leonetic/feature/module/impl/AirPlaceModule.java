package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.UseItemEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.render.BlockRender;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.world.PlacementWorldSnapshot;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1297;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_3965;
import net.minecraft.class_4184;
import net.minecraft.class_239.class_240;

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
   private final Map<class_2338, Long> renderMap = new HashMap<>();

   public AirPlaceModule() {
      super("AirPlace", Category.EXTRA, "Places the block you are holding in mid-air where you are looking.");
   }

   @Override
   public void onDisable() {
      this.renderMap.clear();
   }

   private class_2338 target() {
      if (mc.field_1724 != null && mc.field_1687 != null && !mc.field_1724.method_7325() && NetworkThreadExecutor.isConnected()) {
         if (mc.field_1765 != null && mc.field_1765.method_17783() != class_240.field_1333) {
            return null;
         } else if (this.heldBlock() == null) {
            return null;
         } else {
            class_1297 camera = mc.method_1560();
            if (camera == null) {
               return null;
            } else {
               double reach = this.customRange.getValue() ? this.range.getValue() : mc.field_1724.method_55754();
               if (!(camera.method_5745(reach, 0.0F, false) instanceof class_3965 hit)) {
                  return null;
               } else {
                  class_2338 var7 = hit.method_17777().method_10062();
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
      if (mc.field_1724 != null && !mc.field_1724.method_3144()) {
         class_2338 pos = this.target();
         class_1799 held = this.heldBlock();
         if (pos != null && held != null) {
            Homovore2Client.placements().enqueue(pos, null, held.method_7909(), new PlacementManager.Options(true, false, this.customRange.getValue(), true));
            event.cancel();
         }
      }
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (!this.render.getValue()) {
         this.renderMap.clear();
      } else {
         long now = System.currentTimeMillis();
         class_2338 pos = this.target();
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

   private class_1799 heldBlock() {
      if (mc.field_1724 == null) {
         return null;
      }

      class_1799 held = mc.field_1724.method_6047();
      return held.method_7909() instanceof class_1747 ? held : null;
   }
}
