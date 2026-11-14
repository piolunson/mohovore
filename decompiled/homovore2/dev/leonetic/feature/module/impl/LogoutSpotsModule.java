package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Nametag;
import dev.leonetic.render.PlayerBadges;
import dev.leonetic.render.Render2D;
import dev.leonetic.render.Wireframe;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_4050;
import net.minecraft.class_4184;
import net.minecraft.class_634;
import net.minecraft.class_638;
import net.minecraft.class_742;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class LogoutSpotsModule extends Module {
   private static final double TAG_OFFSET = 0.5;
   public final Setting<Boolean> model = this.bool("Model", true).describe("Draws the player as they stood, rather than only the space they filled.");
   public final Setting<Boolean> heldItems = this.bool("Held Items", true)
      .describe("Draws whatever was in their hands, where their hands were holding it.")
      .visibleWhen(() -> this.model.getValue());
   public final Setting<Boolean> box = this.bool("Box", true).describe("Draws the space the player filled. Drawn anyway when the model is missing.");
   public final Setting<Boolean> fullHeight = this.bool("Full Height", false)
      .describe("Draws the whole box rather than just the footprint it left on the ground.")
      .visibleWhen(() -> this.box.getValue());
   public final Setting<Color> lineColor = this.color("Line Color", 255, 255, 255, 255);
   public final Setting<Color> sideColor = this.color("Side Color", 255, 255, 255, 0);
   public final Setting<Double> lineWidth = this.number("Line Width", (double)3.0, (double)0.1, (double)16.0);
   public final Setting<Boolean> totems = this.bool("Totems", true).describe("Shows how many totems the player had popped when they logged out.");
   public final Setting<Boolean> items = this.bool("Items", true).describe("Shows what they were wearing and holding above the name.");
   public final Setting<Boolean> badges = this.bool("Badges", true).describe("Shows what plugins add to a name, such as the logo and stats of IRC members.");
   public final Setting<Boolean> background = this.bool("Background", true).describe("Draws a backdrop behind the name.");
   public final Setting<Boolean> shadow = this.bool("Shadow", true).describe("Drop shadow behind the name's glyphs.");
   public final Setting<Color> nameColor = this.color("Name", 75, 75, 75, 255);
   public final Setting<Color> totemColor = this.color("Totem", 230, 150, 25, 255).visibleWhen(() -> this.totems.getValue());
   public final Setting<Color> backdropColor = this.color("Backdrop", 0, 0, 0, 75).visibleWhen(() -> this.background.getValue());
   private final Map<UUID, LogoutSpotsModule.Spot> spots = new LinkedHashMap<>();
   private final Map<UUID, LogoutSpotsModule.Spot> lastSeen = new LinkedHashMap<>();
   private class_638 level;

   public LogoutSpotsModule() {
      super("LogoutSpots", Category.RENDER, "Marks where players logged out.");
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   public List<LogoutSpotsModule.LogoutSpot> getSpots() {
      List<LogoutSpotsModule.LogoutSpot> out = new ArrayList<>(this.spots.size());

      for (Entry<UUID, LogoutSpotsModule.Spot> entry : this.spots.entrySet()) {
         LogoutSpotsModule.Spot spot = entry.getValue();
         out.add(
            new LogoutSpotsModule.LogoutSpot(
               entry.getKey(),
               spot.name,
               spot.box,
               spot.health,
               spot.maxHealth,
               spot.pops,
               spot.pos,
               spot.bodyYaw,
               spot.headYaw,
               spot.pitch,
               spot.pose,
               spot.equipment()
            )
         );
      }

      return out;
   }

   private void forget() {
      this.spots.clear();
      this.lastSeen.clear();
      this.level = null;
   }

   @Override
   public void onTick() {
      class_634 connection = mc.method_1562();
      if (mc.field_1687 != null && mc.field_1724 != null && connection != null) {
         if (mc.field_1687 != this.level) {
            this.forget();
            this.level = mc.field_1687;
         }

         Collection<UUID> online = connection.method_31363();
         this.spots.keySet().removeIf(online::contains);

         for (Entry<UUID, LogoutSpotsModule.Spot> entry : this.lastSeen.entrySet()) {
            if (!online.contains(entry.getKey())) {
               this.spots.put(entry.getKey(), entry.getValue());
            }
         }

         this.lastSeen.clear();

         for (class_742 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724) {
               this.lastSeen.put(player.method_5667(), new LogoutSpotsModule.Spot(player));
            }
         }
      }
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (!this.spots.isEmpty()) {
         class_12179 style = class_12179.method_75537(
            this.lineColor.getValue().getRGB(), this.lineWidth.getValue().floatValue(), this.sideColor.getValue().getRGB()
         );

         for (LogoutSpotsModule.Spot spot : this.spots.values()) {
            spot.capture(partialTick);
            boolean drewModel = this.model.getValue() && spot.drawModel(style);
            if (this.box.getValue() || !drewModel) {
               this.drawBox(spot.box, style);
            }
         }
      }
   }

   private void drawBox(class_238 box, class_12179 style) {
      if (this.fullHeight.getValue()) {
         class_12180.method_75542(box, style, false).method_75533();
      } else {
         class_12180.method_75548(
               new class_243(box.field_1323, box.field_1322, box.field_1321),
               new class_243(box.field_1320, box.field_1322, box.field_1324),
               class_2350.field_11036,
               style
            )
            .method_75533();
      }
   }

   @Override
   public void onRender2D(class_332 graphics, float partialTick) {
      if (!this.spots.isEmpty() && mc.field_1687 != null) {
         class_4184 camera = mc.field_1773.method_19418();
         class_243 cameraPos = camera.method_71156();
         Matrix4f viewProjection = new Matrix4f(mc.field_1773.method_22973(partialTick)).mul(new Matrix4f().rotation(camera.method_23767()).invert());
         Vector3f up = Nametag.up(camera);
         Render2D.Surface previous = Render2D.surface(Render2D.Surface.HUD);

         for (LogoutSpotsModule.Spot spot : this.spots.values()) {
            spot.renderTag(graphics, viewProjection, cameraPos, up);
         }

         Render2D.surface(previous);
      }
   }

   public record LogoutSpot(
      UUID id,
      String name,
      class_238 box,
      float health,
      float maxHealth,
      int pops,
      class_243 pos,
      float bodyYaw,
      float headYaw,
      float pitch,
      class_4050 pose,
      Map<class_1304, class_1799> equipment
   ) {
   }

   private class Spot {
      private final UUID id;
      private final class_238 box;
      private final class_243 pos;
      private final String name;
      private final float health;
      private final float maxHealth;
      private final float bodyYaw;
      private final float headYaw;
      private final float pitch;
      private final class_4050 pose;
      private final int pops;
      private final String popText;
      private class_742 player;
      private Wireframe.Capture capture;
      private Map<class_1304, class_1799> equipment;

      private Spot(class_742 player) {
         this.player = player;
         this.id = player.method_5667();
         this.box = player.method_5829();
         this.pos = player.method_73189();
         this.name = player.method_5477().getString();
         this.health = player.method_6032();
         this.maxHealth = player.method_6063();
         this.bodyYaw = player.field_6283;
         this.headYaw = player.method_5791();
         this.pitch = player.method_36455();
         this.pose = player.method_18376();
         this.pops = Homovore2Client.totemPops().getPops(player.method_5667());
         this.popText = this.pops > 0 ? " -" + this.pops : "";
      }

      private void capture(float partialTick) {
         if (this.capture == null) {
            this.equipment();
            this.capture = Wireframe.capture(this.player, partialTick);
            this.player = null;
         }
      }

      private Map<class_1304, class_1799> equipment() {
         if (this.equipment == null) {
            if (this.player == null) {
               this.equipment = Map.of();
            } else {
               Map<class_1304, class_1799> copies = new LinkedHashMap<>();
               Nametag.equipment(this.player).forEach((slot, stack) -> copies.put(slot, stack.method_7972()));
               this.equipment = Collections.unmodifiableMap(copies);
            }
         }

         return this.equipment;
      }

      private boolean drawModel(class_12179 style) {
         Wireframe.draw(this.capture.body(), this.pos, style, true);
         if (LogoutSpotsModule.this.heldItems.getValue()) {
            Wireframe.draw(this.capture.held(), this.pos, style, true);
         }

         return !this.capture.body().isEmpty();
      }

      private void renderTag(class_332 graphics, Matrix4f viewProjection, class_243 cameraPos, Vector3f up) {
         Nametag.Placement placement = Nametag.place(
            graphics, viewProjection, cameraPos, up, this.box.method_1005().field_1352, this.box.field_1325 + 0.5, this.box.method_1005().field_1350
         );
         if (placement != null) {
            boolean shadow = LogoutSpotsModule.this.shadow.getValue();
            boolean showPops = LogoutSpotsModule.this.totems.getValue() && !this.popText.isEmpty();
            PlayerBadges.Badge badge = LogoutSpotsModule.this.badges.getValue() ? PlayerBadges.get(this.id, this.name) : null;
            boolean logo = badge != null && badge.logo();
            List<PlayerBadges.Part> parts = badge == null ? List.of() : badge.parts();
            int logoWidth = Nametag.logoWidth(logo);
            int nameWidth = Render2D.width(this.name);
            int popWidth = showPops ? Render2D.width(this.popText) : 0;
            int textWidth = logoWidth + nameWidth + popWidth + (shadow ? 1 : 0);

            for (PlayerBadges.Part part : parts) {
               textWidth += Nametag.partWidth(part);
            }

            int lineWidth = textWidth + 2;
            int lineHeight = Render2D.textHeight() + 1 + (shadow ? 1 : 0);
            Collection<class_1799> row = LogoutSpotsModule.this.items.getValue() ? this.equipment().values() : List.of();
            int itemsWidth = Nametag.itemsWidth(row);
            int itemsHeight = Nametag.itemsHeight(row);
            Matrix3x2fStack pose = graphics.method_51448();
            pose.pushMatrix();
            pose.translate(placement.x(), placement.y());
            pose.scale(placement.scale(), placement.scale());
            int top = -1;
            if (!row.isEmpty()) {
               Nametag.drawItems(graphics, row, -itemsWidth / 2, top - itemsHeight);
            }

            int left = -lineWidth / 2;
            if (LogoutSpotsModule.this.background.getValue()) {
               Render2D.fill(graphics, left, top, left + lineWidth, top + lineHeight, LogoutSpotsModule.this.backdropColor.getValue().getRGB());
            }

            int textX = left + 1;
            int textY = top + 1;
            if (logo) {
               Nametag.drawLogo(graphics, textX, textY);
               textX += logoWidth;
            }

            Render2D.text(graphics, this.name, textX, textY, LogoutSpotsModule.this.nameColor.getValue().getRGB(), shadow);
            textX += nameWidth;
            if (showPops) {
               Render2D.text(graphics, this.popText, textX, textY, LogoutSpotsModule.this.totemColor.getValue().getRGB(), shadow);
               textX += popWidth;
            }

            for (PlayerBadges.Part part : parts) {
               Nametag.drawPart(graphics, part, textX, textY, shadow);
               textX += Nametag.partWidth(part);
            }

            pose.popMatrix();
         }
      }
   }
}
