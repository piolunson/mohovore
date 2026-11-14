package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PotionEffectManager;
import dev.leonetic.render.Nametag;
import dev.leonetic.render.PlayerBadges;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class NametagsModule extends Module {
   private static final double HEAD_OFFSET = 0.5;
   public final Setting<Boolean> totems = this.bool("Totems", true).describe("Shows how many totems the player has popped since they last died.");
   public final Setting<Boolean> potions = this.bool("Potions", true).describe("Shows the combat potions the player is under, with what each one has left.");
   public final Setting<Boolean> badges = this.bool("Badges", true).describe("Shows what plugins add to a name, such as the logo and stats of IRC members.");
   public final Setting<Boolean> items = this.bool("Items", true).describe("Shows what the player is wearing and holding above the name.");
   public final Setting<Boolean> background = this.bool("Background", true).describe("Draws a backdrop behind the name.");
   public final Setting<Boolean> shadow = this.bool("Shadow", false).describe("Drop shadow behind the name's glyphs.");
   public final Setting<Color> nameColor = this.color("Name", 255, 255, 255, 255);
   public final Setting<Color> friendColor = this.color("Friend", 0, 105, 255, 255);
   public final Setting<Color> enemyColor = this.color("Enemy", 255, 0, 0, 255);
   public final Setting<Color> potionColor = this.color("Potion", 255, 255, 255, 255).visibleWhen(() -> this.potions.getValue());
   public final Setting<Color> totemColor = this.color("Totem", 255, 150, 0, 255).visibleWhen(() -> this.totems.getValue());
   public final Setting<Color> backdropColor = this.color("Backdrop", 0, 0, 0, 128).visibleWhen(() -> this.background.getValue());

   public NametagsModule() {
      super("Nametags", Category.RENDER, "Better entity nametags.");
   }

   @Override
   public void onRender2D(DrawContext graphics, float partialTick) {
      if (mc.world != null && mc.player != null) {
         Camera camera = mc.gameRenderer.getCamera();
         Vec3d cameraPos = camera.getCameraPos();
         Matrix4f viewProjection = new Matrix4f(mc.gameRenderer.getBasicProjectionMatrix(partialTick))
            .mul(new Matrix4f().rotation(camera.getRotation()).invert());
         Vector3f up = Nametag.up(camera);
         List<AbstractClientPlayerEntity> players = new ArrayList<>(mc.world.getPlayers());
         players.sort(Comparator.comparingDouble(playerx -> -playerx.squaredDistanceTo(cameraPos)));
         Render2D.Surface previous = Render2D.surface(Render2D.Surface.HUD);

         for (AbstractClientPlayerEntity player : players) {
            if (this.shouldRender(player)) {
               this.render(graphics, viewProjection, cameraPos, up, player, partialTick);
            }
         }

         Render2D.surface(previous);
      }
   }

   private boolean shouldRender(AbstractClientPlayerEntity player) {
      return player.isInvisible() ? false : player != mc.player || mc.options.getPerspective() != Perspective.FIRST_PERSON;
   }

   private void render(DrawContext graphics, Matrix4f viewProjection, Vec3d cameraPos, Vector3f up, AbstractClientPlayerEntity player, float partialTick) {
      double x = MathHelper.lerp(partialTick, player.lastRenderX, player.getX());
      double y = MathHelper.lerp(partialTick, player.lastRenderY, player.getY()) + player.getHeight() + 0.5;
      double z = MathHelper.lerp(partialTick, player.lastRenderZ, player.getZ());
      Nametag.Placement placement = Nametag.place(graphics, viewProjection, cameraPos, up, x, y, z);
      if (placement != null) {
         String name = player.getName().getString();
         int pops = this.totems.getValue() ? Homovore2Client.totemPops().getPops(player.getUuid()) : 0;
         String popText = pops > 0 ? " -" + pops : "";
         List<PotionEffectManager.Icon> effects = this.potions.getValue() ? Homovore2Client.potions().icons(player) : List.of();
         int potionsWidth = Nametag.potionsWidth(effects);
         PlayerBadges.Badge badge = this.badges.getValue() ? PlayerBadges.get(player.getUuid(), name) : null;
         boolean logo = badge != null && badge.logo();
         List<PlayerBadges.Part> parts = badge == null ? List.of() : badge.parts();
         int logoWidth = Nametag.logoWidth(logo);
         int nameWidth = Render2D.width(name);
         int popWidth = Render2D.width(popText);
         int textWidth = potionsWidth + logoWidth + nameWidth + popWidth + (this.shadow.getValue() ? 1 : 0);

         for (PlayerBadges.Part part : parts) {
            textWidth += Nametag.partWidth(part);
         }

         int lineWidth = textWidth + 2;
         int lineHeight = Render2D.textHeight() + 1 + (this.shadow.getValue() ? 1 : 0);
         int overflow = effects.isEmpty() ? 0 : Math.max(0, (Nametag.potionSize() - Render2D.textHeight() + 1) / 2);
         Collection<ItemStack> equipment = this.items.getValue() ? Nametag.equipment(player).values() : List.of();
         int itemsWidth = Nametag.itemsWidth(equipment);
         int itemsHeight = Nametag.itemsHeight(equipment);
         Matrix3x2fStack pose = graphics.getMatrices();
         pose.pushMatrix();
         pose.translate(placement.x(), placement.y());
         pose.scale(placement.scale(), placement.scale());
         int top = -1 - overflow;
         if (!equipment.isEmpty()) {
            Nametag.drawItems(graphics, equipment, -itemsWidth / 2, top - itemsHeight);
         }

         int left = -lineWidth / 2;
         if (this.background.getValue()) {
            Render2D.fill(graphics, left, top, left + lineWidth, top + lineHeight + overflow * 2, this.backdropColor.getValue().getRGB());
         }

         int textX = left + 1;
         int textY = top + 1 + overflow;
         if (!effects.isEmpty()) {
            textX = Math.round(Nametag.drawPotions(graphics, effects, textX, textY, this.potionColor.getValue().getRGB(), this.shadow.getValue()));
         }

         if (logo) {
            Nametag.drawLogo(graphics, textX, textY);
            textX += logoWidth;
         }

         Render2D.text(graphics, name, textX, textY, this.nameColor(player).getRGB(), this.shadow.getValue());
         textX += nameWidth;
         if (!popText.isEmpty()) {
            Render2D.text(graphics, popText, textX, textY, this.totemColor.getValue().getRGB(), this.shadow.getValue());
            textX += popWidth;
         }

         for (PlayerBadges.Part part : parts) {
            Nametag.drawPart(graphics, part, textX, textY, this.shadow.getValue());
            textX += Nametag.partWidth(part);
         }

         pose.popMatrix();
      }
   }

   private Color nameColor(AbstractClientPlayerEntity player) {
      if (Homovore2Client.enemies().isEnemy(player)) {
         return this.enemyColor.getValue();
      } else {
         return Homovore2Client.friends().isFriend(player) ? this.friendColor.getValue() : this.nameColor.getValue();
      }
   }
}
