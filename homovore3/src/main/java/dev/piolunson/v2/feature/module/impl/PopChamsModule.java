package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.TotemPopEvent;
import dev.piolunson.v2.event.system.Subscribe;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.render.Wireframe;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

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
   private ClientWorld level;

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
      if (this.chams.getValue() && !event.isSelf() && mc.world != null) {
         this.ghosts.add(new PopChamsModule.Ghost(event.getPlayer()));
      }
   }

   @Override
   public void onTick() {
      if (mc.world != this.level) {
         this.forget();
         this.level = mc.world;
      }
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
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
                     Vec3d pos = this.rise.getValue() ? ghost.pos.add(0.0, elapsed / 1000.0 * this.riseSpeed.getValue(), 0.0) : ghost.pos;
                     DrawStyle style = DrawStyle.filledAndStroked(
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
      private final Vec3d pos;
      private final long popped;
      private PlayerEntity player;
      private Wireframe.Capture capture;

      private Ghost(PlayerEntity player) {
         this.player = player;
         this.pos = player.getEntityPos();
         this.popped = System.currentTimeMillis();
      }

      private void capture(float partialTick) {
         this.capture = Wireframe.capture(this.player, partialTick);
         this.player = null;
      }
   }
}
