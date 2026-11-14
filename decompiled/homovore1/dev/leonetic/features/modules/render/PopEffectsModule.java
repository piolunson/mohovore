package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.TotemPopEvent;
import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.WireframeEntityRenderer;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_1657;
import net.minecraft.class_1937;
import net.minecraft.class_2396;
import net.minecraft.class_2398;
import net.minecraft.class_243;
import net.minecraft.class_5321;

public class PopEffectsModule extends Module {
   public final Setting<Color> primaryColor = this.color("Primary", 232, 255, 85, 255).setPage("Particles");
   public final Setting<Color> accentColor = this.color("Accent", 85, 255, 85, 255).setPage("Particles");
   public final Setting<Float> scale = this.num("Scale", 1.0F, 0.1F, 5.0F).setPage("Particles");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", false).setPage("Particles");
   public final Setting<Boolean> ghosts = this.bool("Ghosts", true).setPage("Ghosts");
   public final Setting<Float> fadeTime = this.num("FadeTime", 2.0F, 0.1F, 15.0F).setPage("Ghosts");
   public final Setting<Color> ghostColor = this.color("GhostColor", 232, 255, 85, 255).setPage("Ghosts");
   public final Setting<Color> ghostSideColor = this.color("GhostSideColor", 232, 255, 85, 55).setPage("Ghosts");
   public final Setting<Float> ghostLineWidth = this.num("GhostLineWidth", 1.5F, 0.5F, 5.0F).setPage("Ghosts");
   public final Setting<Boolean> move = this.bool("Move", false).setPage("Ghosts");
   public final Setting<Float> moveSpeed = this.num("MoveSpeed", 1.0F, 0.05F, 10.0F).setPage("Ghosts");
   private final List<PopEffectsModule.Ghost> ghostList = new CopyOnWriteArrayList<>();

   public PopEffectsModule() {
      super("PopEffects", "Customize totem pop particle effects", Module.Category.RENDER);
      this.fadeTime.setVisibility(v -> this.ghosts.getValue());
      this.ghostColor.setVisibility(v -> this.ghosts.getValue());
      this.ghostSideColor.setVisibility(v -> this.ghosts.getValue());
      this.ghostLineWidth.setVisibility(v -> this.ghosts.getValue());
      this.move.setVisibility(v -> this.ghosts.getValue());
      this.moveSpeed.setVisibility(v -> this.ghosts.getValue() && this.move.getValue());
   }

   public static PopEffectsModule get() {
      return Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(PopEffectsModule.class);
   }

   public boolean shouldCustomize(class_2396<?> type) {
      return this.isEnabled() && type == class_2398.field_11220;
   }

   public int getRgb(boolean yellowVariant) {
      Color c = yellowVariant ? this.primaryColor.getValue() : this.accentColor.getValue();
      return c.getRed() << 16 | c.getGreen() << 8 | c.getBlue();
   }

   public float getScale() {
      return Math.max(0.1F, this.scale.getValue());
   }

   @Override
   public void onEnable() {
      this.ghostList.clear();
   }

   @Override
   public void onDisable() {
      this.ghostList.clear();
   }

   @Subscribe
   public void onTotemPop(TotemPopEvent event) {
      if (this.ghosts.getValue() && !nullCheck()) {
         if (event.getEntity() instanceof class_1657 player) {
            if (player != mc.field_1724) {
               this.ghostList.add(new PopEffectsModule.Ghost(player, mc.field_1687.method_27983()));
            }
         }
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.ghostList.clear();
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck() && !this.ghostList.isEmpty()) {
         if (!this.ghosts.getValue()) {
            this.ghostList.clear();
         } else {
            class_5321<class_1937> dim = mc.field_1687.method_27983();
            long fadeMillis = (long)(this.fadeTime.getValue() * 1000.0F);
            long now = System.currentTimeMillis();

            for (PopEffectsModule.Ghost ghost : this.ghostList) {
               long elapsed = now - ghost.popTime;
               if (elapsed >= fadeMillis) {
                  this.ghostList.remove(ghost);
               } else if (dim.equals(ghost.dimension)) {
                  float fade = 1.0F - (float)elapsed / (float)fadeMillis;
                  class_243 pos = this.move.getValue() ? ghost.pos.method_1031(0.0, elapsed / 1000.0 * this.moveSpeed.getValue().floatValue(), 0.0) : ghost.pos;
                  ghost.capturedGeometry = WireframeEntityRenderer.render(
                     event.getMatrix(),
                     ghost.entity,
                     pos,
                     ghost.capturedGeometry,
                     event.getDelta(),
                     withFade(this.ghostSideColor.getValue(), fade),
                     withFade(this.ghostColor.getValue(), fade),
                     this.ghostLineWidth.getValue()
                  );
               }
            }
         }
      }
   }

   private static Color withFade(Color color, float fade) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * fade));
   }

   private static class Ghost {
      final class_1657 entity;
      final class_243 pos;
      final class_5321<class_1937> dimension;
      final long popTime;
      List<float[][]> capturedGeometry = null;

      Ghost(class_1657 entity, class_5321<class_1937> dimension) {
         this.entity = entity;
         this.pos = entity.method_73189();
         this.dimension = dimension;
         this.popTime = System.currentTimeMillis();
      }
   }
}
