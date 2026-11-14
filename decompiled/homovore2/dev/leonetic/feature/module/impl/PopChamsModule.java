package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.TotemPopEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Wireframe;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.class_12179;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import net.minecraft.class_638;

public class PopChamsModule extends Module {
   public final Setting<Boolean> chams = this.bool("Chams", true).page("Chams").describe("Leaves a wireframe of the player where they popped.");
   public final Setting<Double> fade = this.number("Fade", (double)2.0, (double)0.1, (double)15.0)
      .page("Chams")
      .describe("Seconds a wireframe takes to fade out.")
      .visibleWhen(() -> this.chams.getValue());
   public final Setting<Color> lineColor = this.color("Line Color", 0, 145, 180, 255).page("Chams").visibleWhen(() -> this.chams.getValue());
   public final Setting<Color> sideColor = this.color("Side Color", 0, 145, 180, 44).page("Chams").visibleWhen(() -> this.chams.getValue());
   public final Setting<Double> lineWidth = this.number("Line Width", (double)5.0, (double)0.1, (double)16.0)
      .page("Chams")
      .visibleWhen(() -> this.chams.getValue());
   public final Setting<Boolean> heldItems = this.bool("Held Items", true)
      .page("Chams")
      .describe("Draws whatever was in their hands, where their hands were holding it.")
      .visibleWhen(() -> this.chams.getValue());
   public final Setting<Boolean> rise = this.bool("Rise", true)
      .page("Chams")
      .describe("Floats the wireframe upwards as it fades, rather than leaving it standing.")
      .visibleWhen(() -> this.chams.getValue());
   public final Setting<Double> riseSpeed = this.number("Rise Speed", (double)1.5, (double)0.05, (double)10.0)
      .page("Chams")
      .describe("Blocks per second.")
      .visibleWhen(() -> this.chams.getValue() && this.rise.getValue());
   public final Setting<Boolean> particles = this.bool("Particles", true).page("Particles").describe("Recolours and resizes the totem particles themselves.");
   public final Setting<Color> primary = this.color("Primary", 0, 145, 180, 255)
      .page("Particles")
      .describe("Replaces the pale half of the particles vanilla spits out.")
      .visibleWhen(() -> this.particles.getValue());
   public final Setting<Color> accent = this.color("Accent", 0, 205, 255, 255)
      .page("Particles")
      .describe("Replaces the green half.")
      .visibleWhen(() -> this.particles.getValue());
   public final Setting<Double> scale = this.number("Scale", (double)0.6, (double)0.1, (double)5.0)
      .page("Particles")
      .describe("Multiplies the size of every totem particle.")
      .visibleWhen(() -> this.particles.getValue());
   public final Setting<Boolean> particlesThroughWalls = this.bool("Through Walls", true)
      .page("Particles")
      .describe("Draws the particles over whatever is standing in front of them.")
      .visibleWhen(() -> this.particles.getValue());
   private final List<PopChamsModule.Ghost> ghosts = new ArrayList<>();
   private class_638 level;

   public PopChamsModule() {
      super("PopChams", Category.RENDER, "Draws what is left behind when a totem pops.");
   }

   @Override
   public void onEnable() {
      this.forget();
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   private void forget() {
      this.ghosts.clear();
      this.level = null;
   }

   @Subscribe
   public void onTotemPop(TotemPopEvent event) {
      if (this.chams.getValue() && !event.isSelf() && mc.field_1687 != null) {
         this.ghosts.add(new PopChamsModule.Ghost(event.getPlayer()));
      }
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != this.level) {
         this.forget();
         this.level = mc.field_1687;
      }
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (!this.ghosts.isEmpty()) {
         if (!this.chams.getValue()) {
            this.ghosts.clear();
         } else {
            long lifetime = (long)(this.fade.getValue() * 1000.0);
            long now = System.currentTimeMillis();
            Iterator<PopChamsModule.Ghost> iterator = this.ghosts.iterator();

            while (iterator.hasNext()) {
               PopChamsModule.Ghost ghost = iterator.next();
               long elapsed = now - ghost.popped;
               if (elapsed >= lifetime) {
                  iterator.remove();
               } else {
                  if (ghost.capture == null) {
                     ghost.capture(partialTick);
                  }

                  if (ghost.capture.isEmpty()) {
                     iterator.remove();
                  } else {
                     float left = 1.0F - (float)elapsed / (float)lifetime;
                     class_243 pos = this.rise.getValue() ? ghost.pos.method_1031(0.0, elapsed / 1000.0 * this.riseSpeed.getValue(), 0.0) : ghost.pos;
                     class_12179 style = class_12179.method_75537(
                        faded(this.lineColor.getValue(), left), this.lineWidth.getValue().floatValue(), faded(this.sideColor.getValue(), left)
                     );
                     Wireframe.draw(ghost.capture.body(), pos, style, true);
                     if (this.heldItems.getValue()) {
                        Wireframe.draw(ghost.capture.held(), pos, style, true);
                     }
                  }
               }
            }
         }
      }
   }

   private static int faded(Color color, float left) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * left)).getRGB();
   }

   public boolean customizesParticles() {
      return this.isEnabled() && this.particles.getValue();
   }

   public int particleColor(boolean pale) {
      return (pale ? this.primary.getValue() : this.accent.getValue()).getRGB() & 16777215;
   }

   public float particleScale() {
      return this.scale.getValue().floatValue();
   }

   public static PopChamsModule active() {
      if (Homovore2Client.modules() == null) {
         return null;
      }

      PopChamsModule module = Homovore2Client.modules().get(PopChamsModule.class);
      return module != null && module.isEnabled() ? module : null;
   }

   private static final class Ghost {
      private final class_243 pos;
      private final long popped;
      private class_1657 player;
      private Wireframe.Capture capture;

      private Ghost(class_1657 player) {
         this.player = player;
         this.pos = player.method_73189();
         this.popped = System.currentTimeMillis();
      }

      private void capture(float partialTick) {
         this.capture = Wireframe.capture(this.player, partialTick);
         this.player = null;
      }
   }
}
