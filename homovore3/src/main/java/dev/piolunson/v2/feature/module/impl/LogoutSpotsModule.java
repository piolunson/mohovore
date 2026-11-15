package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.render.Nametag;
import dev.piolunson.v2.render.PlayerBadges;
import dev.piolunson.v2.render.Render2D;
import dev.piolunson.v2.render.Wireframe;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
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
   private ClientWorld level;

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
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      if (mc.world != null && mc.player != null && connection != null) {
         if (mc.world != this.level) {
            this.forget();
            this.level = mc.world;
         }

         Collection<UUID> online = connection.getPlayerUuids();
         this.spots.keySet().removeIf(online::contains);

         for (Entry<UUID, LogoutSpotsModule.Spot> entry : this.lastSeen.entrySet()) {
            if (!online.contains(entry.getKey())) {
               this.spots.put(entry.getKey(), entry.getValue());
            }
         }

         this.lastSeen.clear();

         for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player) {
               this.lastSeen.put(player.getUuid(), new LogoutSpotsModule.Spot(player));
            }
         }
      }
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (!this.spots.isEmpty()) {
         DrawStyle style = DrawStyle.filledAndStroked(
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

   private void drawBox(Box box, DrawStyle style) {
      if (this.fullHeight.getValue()) {
         GizmoDrawing.box(box, style, false).ignoreOcclusion();
      } else {
         GizmoDrawing.face(new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.maxZ), Direction.UP, style).ignoreOcclusion();
      }
   }

   @Override
   public void onRender2D(DrawContext graphics, float partialTick) {
      if (!this.spots.isEmpty() && mc.world != null) {
         Camera camera = mc.gameRenderer.getCamera();
         Vec3d cameraPos = camera.getCameraPos();
         Matrix4f viewProjection = new Matrix4f(mc.gameRenderer.getBasicProjectionMatrix(partialTick))
            .mul(new Matrix4f().rotation(camera.getRotation()).invert());
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
      Box box,
      float health,
      float maxHealth,
      int pops,
      Vec3d pos,
      float bodyYaw,
      float headYaw,
      float pitch,
      EntityPose pose,
      Map<EquipmentSlot, ItemStack> equipment
   ) {
   }

   private class Spot {
      private final UUID id;
      private final Box box;
      private final Vec3d pos;
      private final String name;
      private final float health;
      private final float maxHealth;
      private final float bodyYaw;
      private final float headYaw;
      private final float pitch;
      private final EntityPose pose;
      private final int pops;
      private final String popText;
      private AbstractClientPlayerEntity player;
      private Wireframe.Capture capture;
      private Map<EquipmentSlot, ItemStack> equipment;

      private Spot(AbstractClientPlayerEntity player) {
         this.player = player;
         this.id = player.getUuid();
         this.box = player.getBoundingBox();
         this.pos = player.getEntityPos();
         this.name = player.getName().getString();
         this.health = player.getHealth();
         this.maxHealth = player.getMaxHealth();
         this.bodyYaw = player.bodyYaw;
         this.headYaw = player.getHeadYaw();
         this.pitch = player.getPitch();
         this.pose = player.getPose();
         this.pops = Homovore2Client.totemPops().getPops(player.getUuid());
         this.popText = this.pops > 0 ? " -" + this.pops : "";
      }

      private void capture(float partialTick) {
         if (this.capture == null) {
            this.equipment();
            this.capture = Wireframe.capture(this.player, partialTick);
            this.player = null;
         }
      }

      private Map<EquipmentSlot, ItemStack> equipment() {
         if (this.equipment == null) {
            if (this.player == null) {
               this.equipment = Map.of();
            } else {
               Map<EquipmentSlot, ItemStack> copies = new LinkedHashMap<>();
               Nametag.equipment(this.player).forEach((slot, stack) -> copies.put(slot, stack.copy()));
               this.equipment = Collections.unmodifiableMap(copies);
            }
         }

         return this.equipment;
      }

      private boolean drawModel(DrawStyle style) {
         Wireframe.draw(this.capture.body(), this.pos, style, true);
         if (LogoutSpotsModule.this.heldItems.getValue()) {
            Wireframe.draw(this.capture.held(), this.pos, style, true);
         }

         return !this.capture.body().isEmpty();
      }

      private void renderTag(DrawContext graphics, Matrix4f viewProjection, Vec3d cameraPos, Vector3f up) {
         Nametag.Placement placement = Nametag.place(
            graphics, viewProjection, cameraPos, up, this.box.getCenter().x, this.box.maxY + 0.5, this.box.getCenter().z
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
            Collection<ItemStack> row = LogoutSpotsModule.this.items.getValue() ? this.equipment().values() : List.of();
            int itemsWidth = Nametag.itemsWidth(row);
            int itemsHeight = Nametag.itemsHeight(row);
            Matrix3x2fStack pose = graphics.getMatrices();
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
