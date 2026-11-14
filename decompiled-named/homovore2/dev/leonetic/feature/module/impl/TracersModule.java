package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.EntityDataSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.feature.setting.SettingGroup;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

public class TracersModule extends Module {
   private static final double START_DISTANCE = 0.1;
   public final EntityDataSetting<TracersModule.TracerData> entities = this.register(
      new EntityDataSetting<>("Entities", TracersModule.TracerData::new, List.of(EntityType.PLAYER))
   );
   public final Setting<TracersModule.Target> target = this.mode("Target", TracersModule.Target.BODY);
   public final Setting<Double> range = this.number("Range", (double)1024.0, (double)0.0, (double)1024.0).describe("Maximum distance in blocks.");
   public final Setting<Boolean> invisible = this.bool("Invisible", true).describe("Includes invisible entities.");
   public final Setting<Boolean> self = this.bool("Self", false).describe("Draws to your own player while using a third-person camera.");

   public TracersModule() {
      super("Tracers", Category.RENDER, "Draws lines to selected entity types.");
   }

   public TracersModule.TracerData getTracer(EntityType<?> type) {
      return this.entities.contains(type) ? this.entities.get(type) : null;
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (mc.world != null && mc.player != null) {
         Vec3d start = camera.getCameraPos().add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(0.1));
         double rangeSquared = this.range.getValue() * this.range.getValue();

         for (Entity entity : mc.world.getEntities()) {
            TracersModule.TracerData tracer = this.getTracer(entity.getType());
            if (tracer != null && this.shouldDraw(entity, camera.getCameraPos(), rangeSquared)) {
               Vec3d feet = entity.getLerpedPos(partialTick);
               double height = entity.getHeight();

               double landing = switch ((TracersModule.Target)this.target.getValue()) {
                  case FEET -> 0.0;
                  case BODY -> height / 2.0;
                  case HEAD -> height;
               };
               int color = tracer.color.getValue().getRGB();
               float width = tracer.width.getValue().floatValue();
               GizmoDrawing.line(start, feet.add(0.0, landing, 0.0), color, width).ignoreOcclusion();
               if (tracer.stem.getValue()) {
                  GizmoDrawing.line(feet, feet.add(0.0, height, 0.0), color, width).ignoreOcclusion();
               }
            }
         }
      }
   }

   private boolean shouldDraw(Entity entity, Vec3d cameraPosition, double rangeSquared) {
      if (entity.squaredDistanceTo(cameraPosition) > rangeSquared) {
         return false;
      } else {
         return entity == mc.player
            ? this.self.getValue() && mc.options.getPerspective() != Perspective.FIRST_PERSON
            : this.invisible.getValue() || !entity.isInvisible();
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
