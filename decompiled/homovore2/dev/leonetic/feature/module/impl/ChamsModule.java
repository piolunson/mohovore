package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.EntityDataSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.feature.setting.SettingGroup;
import dev.leonetic.render.ChamsChain;
import dev.leonetic.render.EntityRenderStateAccess;
import java.awt.Color;
import java.util.List;
import net.minecraft.class_10017;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_5498;

public class ChamsModule extends Module {
   public final EntityDataSetting<ChamsModule.ChamsData> entities = this.register(
      new EntityDataSetting<>("Entities", ChamsModule.ChamsData::new, List.of(class_1299.field_6097))
   );
   public final Setting<Boolean> outline = this.bool("Outline", true).page("Style");
   public final Setting<Integer> outlineWidth = this.integer("OutlineWidth", 5, 1, 16).page("Style").visibleWhen(() -> this.outline.getValue());
   public final Setting<Float> outlineAlpha = this.number("OutlineAlpha", 1.0F, 0.0F, 1.0F).page("Style").visibleWhen(() -> this.outline.getValue());
   public final Setting<Boolean> glow = this.bool("Glow", false).page("Style");
   public final Setting<Integer> glowRadius = this.integer("GlowRadius", 6, 1, 16).page("Style").visibleWhen(() -> this.glow.getValue());
   public final Setting<Float> glowIntensity = this.number("GlowIntensity", 1.2F, 0.0F, 3.0F).page("Style").visibleWhen(() -> this.glow.getValue());
   public final Setting<Boolean> fill = this.bool("Fill", true).page("Style");
   public final Setting<ChamsChain.Fill> fillShader = this.mode("FillShader", ChamsChain.Fill.SOLID)
      .page("Style")
      .visibleWhen(() -> this.fill.getValue())
      .describe(
         "SOLID paints the entity's own colour; the others are animated shaders drawn in screen space, which the silhouette looks through. PHANTOM ignores the entity's colour, TURBULENCE tints its noise with it."
      );
   public final Setting<Float> fillAlpha = this.number("FillAlpha", 0.3F, 0.0F, 1.0F).page("Style").visibleWhen(() -> this.fill.getValue());
   public final Setting<Float> fillSpeed = this.number("FillSpeed", 1.0F, 0.0F, 4.0F)
      .page("Style")
      .visibleWhen(() -> this.fill.getValue() && this.fillShader.getValue() != ChamsChain.Fill.SOLID)
      .describe("How fast the fill shader animates.");

   public ChamsModule() {
      super("Chams", Category.RENDER, "Shades selected entity types through walls.");
      this.entities.page("Entities").describe("Right click a selected type to colour it and set its range.");
   }

   @Override
   public void onDisable() {
      ChamsChain.cleanup();
   }

   public ChamsChain.Style style() {
      boolean filling = this.fill.getValue();
      return new ChamsChain.Style(
         this.outline.getValue() ? this.outlineWidth.getValue() : 0,
         this.outlineAlpha.getValue(),
         this.glow.getValue() ? this.glowRadius.getValue() : 0,
         this.glowIntensity.getValue(),
         filling ? this.fillAlpha.getValue() : 0.0F,
         filling ? this.fillShader.getValue() : ChamsChain.Fill.SOLID,
         this.fillSpeed.getValue()
      );
   }

   public boolean isDrawing() {
      return this.style().isVisible();
   }

   public boolean shades(class_1297 entity) {
      return this.isDrawing() && this.drawn(entity) != null;
   }

   public int colorOf(class_1297 entity) {
      ChamsModule.ChamsData data = this.drawn(entity);
      int color = data == null ? 16777215 : data.color.getValue().getRGB() & 16777215;
      KillAuraModule aura = KillAuraModule.active();
      if (aura == null) {
         return color;
      }

      float amount = aura.pulseAmount(entity);
      return amount <= 0.0F ? color : blend(color, aura.pulseColor(), Math.min(1.0F, amount));
   }

   private static int blend(int from, int to, float amount) {
      int rgb = 0;

      for (int shift = 16; shift >= 0; shift -= 8) {
         int start = from >> shift & 0xFF;
         int end = to >> shift & 0xFF;
         rgb |= Math.round(start + (end - start) * amount) << shift;
      }

      return rgb;
   }

   public boolean seesThrough(class_10017 state) {
      if (!(state instanceof EntityRenderStateAccess access)) {
         return false;
      } else {
         ChamsModule.ChamsData data = this.drawn(access.homovore$getEntity());
         return data != null && data.seeThrough.getValue();
      }
   }

   private ChamsModule.ChamsData drawn(class_1297 entity) {
      if (entity != null && mc.field_1724 != null) {
         if (!this.entities.contains(entity.method_5864())) {
            return null;
         }

         ChamsModule.ChamsData data = this.entities.get(entity.method_5864());
         if (entity != mc.field_1724 || data.self.getValue() && mc.field_1690.method_31044() != class_5498.field_26664) {
            if (!data.invisible.getValue() && entity.method_5767()) {
               return null;
            } else {
               return entity.method_5739(mc.field_1724) <= data.range.getValue() ? data : null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static ChamsModule active() {
      if (Homovore2Client.modules() == null) {
         return null;
      }

      ChamsModule module = Homovore2Client.modules().get(ChamsModule.class);
      return module != null && module.isEnabled() ? module : null;
   }

   public static class ChamsData extends SettingGroup {
      public final Setting<Color> color = this.register(new Setting<>("Color", new Color(255, 60, 60)));
      public final Setting<Double> range = this.register(new Setting<>("Range", 128.0, 0.0, 512.0)).describe("Maximum distance in blocks.");
      public final Setting<Boolean> invisible = this.register(new Setting<>("Invisible", true)).describe("Draws this type even while it is invisible.");
      public final Setting<Boolean> self = this.register(new Setting<>("Self", false)).describe("Draws you as well, while using a third-person camera.");
      public final Setting<Boolean> seeThrough = this.register(new Setting<>("SeeThrough", false))
         .describe("Draws the entity itself over whatever is standing in front of it.");
   }
}
