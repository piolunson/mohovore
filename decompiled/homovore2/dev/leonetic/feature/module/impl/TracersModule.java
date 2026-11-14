package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.EntityDataSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.feature.setting.SettingGroup;
import java.awt.Color;
import java.util.List;
import net.minecraft.class_12180;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import net.minecraft.class_5498;

public class TracersModule extends Module {
   private static final double START_DISTANCE = 0.1;
   public final EntityDataSetting<TracersModule.TracerData> entities = this.register(
      new EntityDataSetting<>("Entities", TracersModule.TracerData::new, List.of(class_1299.field_6097))
   );
   public final Setting<TracersModule.Target> target = this.mode("Target", TracersModule.Target.BODY);
   public final Setting<Double> range = this.number("Range", (double)1024.0, (double)0.0, (double)1024.0).describe("Maximum distance in blocks.");
   public final Setting<Boolean> invisible = this.bool("Invisible", true).describe("Includes invisible entities.");
   public final Setting<Boolean> self = this.bool("Self", false).describe("Draws to your own player while using a third-person camera.");

   public TracersModule() {
      super("Tracers", Category.RENDER, "Draws lines to selected entity types.");
   }

   public TracersModule.TracerData getTracer(class_1299<?> type) {
      return this.entities.contains(type) ? this.entities.get(type) : null;
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         class_243 start = camera.method_71156().method_1019(class_243.method_1030(camera.method_19329(), camera.method_19330()).method_1021(0.1));
         double rangeSquared = this.range.getValue() * this.range.getValue();

         for (class_1297 entity : mc.field_1687.method_18112()) {
            TracersModule.TracerData tracer = this.getTracer(entity.method_5864());
            if (tracer != null && this.shouldDraw(entity, camera.method_71156(), rangeSquared)) {
               class_243 feet = entity.method_30950(partialTick);
               double height = entity.method_17682();

               double landing = switch ((TracersModule.Target)this.target.getValue()) {
                  case FEET -> 0.0;
                  case BODY -> height / 2.0;
                  case HEAD -> height;
               };
               int color = tracer.color.getValue().getRGB();
               float width = tracer.width.getValue().floatValue();
               class_12180.method_75546(start, feet.method_1031(0.0, landing, 0.0), color, width).method_75533();
               if (tracer.stem.getValue()) {
                  class_12180.method_75546(feet, feet.method_1031(0.0, height, 0.0), color, width).method_75533();
               }
            }
         }
      }
   }

   private boolean shouldDraw(class_1297 entity, class_243 cameraPosition, double rangeSquared) {
      if (entity.method_5707(cameraPosition) > rangeSquared) {
         return false;
      } else {
         return entity == mc.field_1724
            ? this.self.getValue() && mc.field_1690.method_31044() != class_5498.field_26664
            : this.invisible.getValue() || !entity.method_5767();
      }
   }

   public enum Target {
      FEET,
      BODY,
      HEAD;
   }

   public static class TracerData extends SettingGroup {
      public final Setting<Color> color = this.register(new Setting<>("Color", new Color(255, 255, 255, 160)));
      public final Setting<Boolean> stem = this.register(new Setting<>("Stem", true));
      public final Setting<Double> width = this.register(new Setting<>("Width", 3.0, 0.1, 16.0));
   }
}
