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
import net.minecraft.class_1799;
import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_3532;
import net.minecraft.class_4184;
import net.minecraft.class_5498;
import net.minecraft.class_742;
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
   public void onRender2D(class_332 graphics, float partialTick) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         class_4184 camera = mc.field_1773.method_19418();
         class_243 cameraPos = camera.method_71156();
         Matrix4f viewProjection = new Matrix4f(mc.field_1773.method_22973(partialTick)).mul(new Matrix4f().rotation(camera.method_23767()).invert());
         Vector3f up = Nametag.up(camera);
         List<class_742> players = new ArrayList<>(mc.field_1687.method_18456());
         players.sort(Comparator.comparingDouble(playerx -> -playerx.method_5707(cameraPos)));
         Render2D.Surface previous = Render2D.surface(Render2D.Surface.HUD);

         for (class_742 player : players) {
            if (this.shouldRender(player)) {
               this.render(graphics, viewProjection, cameraPos, up, player, partialTick);
            }
         }

         Render2D.surface(previous);
      }
   }

   private boolean shouldRender(class_742 player) {
      return player.method_5767() ? false : player != mc.field_1724 || mc.field_1690.method_31044() != class_5498.field_26664;
   }

   private void render(class_332 graphics, Matrix4f viewProjection, class_243 cameraPos, Vector3f up, class_742 player, float partialTick) {
      double x = class_3532.method_16436(partialTick, player.field_6038, player.method_23317());
      double y = class_3532.method_16436(partialTick, player.field_5971, player.method_23318()) + player.method_17682() + 0.5;
      double z = class_3532.method_16436(partialTick, player.field_5989, player.method_23321());
      Nametag.Placement placement = Nametag.place(graphics, viewProjection, cameraPos, up, x, y, z);
      if (placement != null) {
         String name = player.method_5477().getString();
         int pops = this.totems.getValue() ? Homovore2Client.totemPops().getPops(player.method_5667()) : 0;
         String popText = pops > 0 ? " -" + pops : "";
         List<PotionEffectManager.Icon> effects = this.potions.getValue() ? Homovore2Client.potions().icons(player) : List.of();
         int potionsWidth = Nametag.potionsWidth(effects);
         PlayerBadges.Badge badge = this.badges.getValue() ? PlayerBadges.get(player.method_5667(), name) : null;
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
         Collection<class_1799> equipment = this.items.getValue() ? Nametag.equipment(player).values() : List.of();
         int itemsWidth = Nametag.itemsWidth(equipment);
         int itemsHeight = Nametag.itemsHeight(equipment);
         Matrix3x2fStack pose = graphics.method_51448();
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

   private Color nameColor(class_742 player) {
      if (Homovore2Client.enemies().isEnemy(player)) {
         return this.enemyColor.getValue();
      } else {
         return Homovore2Client.friends().isFriend(player) ? this.friendColor.getValue() : this.nameColor.getValue();
      }
   }
}
