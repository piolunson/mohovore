package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import java.awt.Color;
import net.minecraft.class_1297;
import net.minecraft.class_1429;
import net.minecraft.class_1511;
import net.minecraft.class_1542;
import net.minecraft.class_1588;
import net.minecraft.class_1676;
import net.minecraft.class_742;
import net.minecraft.class_746;

public class ShadersModule extends Module {
   public Setting<ShadersModule.Mode> mode = this.mode("Mode", ShadersModule.Mode.Default).setPage("General");
   public Setting<Boolean> glow = this.bool("Glow", true).setPage("General");
   public Setting<Float> glowRadius = this.num("GlowRadius", 3.0F, 0.0F, 16.0F).setPage("General");
   public Setting<Float> glowIntensity = this.num("GlowIntensity", 1.55F, 0.0F, 3.0F).setPage("General");
   public Setting<Float> fillTint = this.num("FillTint", 1.0F, 0.0F, 2.0F).setPage("General");
   public Setting<Float> fillAlpha = this.num("FillAlpha", 0.4F, 0.0F, 1.0F).setPage("General");
   public Setting<Boolean> players = this.bool("Players", true).setPage("Entities");
   public Setting<Float> playerRange = this.num("PlayerRange", 64.0F, 4.0F, 256.0F).setPage("Entities");
   public Setting<Boolean> friends = this.bool("Friends", true).setPage("Entities");
   public Setting<Float> friendRange = this.num("FriendRange", 64.0F, 4.0F, 256.0F).setPage("Entities");
   public Setting<Boolean> monsters = this.bool("Monsters", true).setPage("Entities");
   public Setting<Float> monsterRange = this.num("MonsterRange", 64.0F, 4.0F, 256.0F).setPage("Entities");
   public Setting<Boolean> animals = this.bool("Animals", false).setPage("Entities");
   public Setting<Float> animalRange = this.num("AnimalRange", 64.0F, 4.0F, 256.0F).setPage("Entities");
   public Setting<Boolean> items = this.bool("Items", false).setPage("Entities");
   public Setting<Float> itemRange = this.num("ItemRange", 64.0F, 4.0F, 256.0F).setPage("Entities");
   public Setting<Boolean> crystals = this.bool("Crystals", true).setPage("Entities");
   public Setting<Float> crystalRange = this.num("CrystalRange", 64.0F, 4.0F, 256.0F).setPage("Entities");
   public Setting<Boolean> projectiles = this.bool("Projectiles", false).setPage("Entities");
   public Setting<Float> projectileRange = this.num("ProjectileRange", 64.0F, 4.0F, 256.0F).setPage("Entities");
   public Setting<Color> playerColor = this.color("PlayerOutline", 255, 0, 0, 255).setPage("Colors");
   public Setting<Color> friendColor = this.color("FriendOutline", 0, 255, 100, 255).setPage("Colors");
   public Setting<Color> monsterColor = this.color("MonsterOutline", 200, 60, 60, 255).setPage("Colors");
   public Setting<Color> animalColor = this.color("AnimalOutline", 255, 200, 60, 255).setPage("Colors");
   public Setting<Color> itemColor = this.color("ItemOutline", 255, 255, 255, 255).setPage("Colors");
   public Setting<Color> crystalColor = this.color("CrystalOutline", 0, 200, 255, 255).setPage("Colors");
   public Setting<Color> projectileColor = this.color("ProjectileOutline", 200, 200, 0, 255).setPage("Colors");
   public Setting<Boolean> hand = this.bool("HandShaders", false).setPage("Hand");
   public Setting<Boolean> handOutline = this.bool("HandOutline", true).setPage("Hand");
   public Setting<Float> handThickness = this.num("HandThickness", 2.0F, 1.0F, 10.0F).setPage("Hand");
   public Setting<Boolean> handFill = this.bool("HandFill", false).setPage("Hand");
   public Setting<Boolean> handGlow = this.bool("HandGlow", false).setPage("Hand");
   public Setting<Float> handGlowRadius = this.num("HandGlowRadius", 4.0F, 1.0F, 16.0F).setPage("Hand");
   public Setting<Float> handGlowIntensity = this.num("HandGlowIntensity", 1.0F, 0.0F, 3.0F).setPage("Hand");
   public Setting<Color> handColor = this.color("HandColor", 255, 0, 0, 255).setPage("Hand");

   public ShadersModule() {
      super("Shaders", "Stylised post-effect shader on selected entities", Module.Category.RENDER);
      this.glow.setVisibility(v -> this.mode.getValue() == ShadersModule.Mode.Default);
      this.glowRadius.setVisibility(v -> this.mode.getValue() == ShadersModule.Mode.Default && this.glow.getValue());
      this.glowIntensity.setVisibility(v -> this.mode.getValue() == ShadersModule.Mode.Default && this.glow.getValue());
      this.playerRange.setVisibility(v -> this.players.getValue());
      this.friendRange.setVisibility(v -> this.friends.getValue());
      this.monsterRange.setVisibility(v -> this.monsters.getValue());
      this.animalRange.setVisibility(v -> this.animals.getValue());
      this.itemRange.setVisibility(v -> this.items.getValue());
      this.crystalRange.setVisibility(v -> this.crystals.getValue());
      this.projectileRange.setVisibility(v -> this.projectiles.getValue());
      this.handThickness.setVisibility(v -> this.hand.getValue() && this.handOutline.getValue());
      this.handOutline.setVisibility(v -> this.hand.getValue());
      this.handFill.setVisibility(v -> this.hand.getValue());
      this.handGlow.setVisibility(v -> this.hand.getValue());
      this.handGlowRadius.setVisibility(v -> this.hand.getValue() && this.handGlow.getValue());
      this.handGlowIntensity.setVisibility(v -> this.hand.getValue() && this.handGlow.getValue());
      this.handColor.setVisibility(v -> this.hand.getValue());
   }

   public boolean wantsHandShader() {
      return this.isEnabled() && this.hand.getValue();
   }

   public int getHandThickness() {
      return Math.round(this.handThickness.getValue());
   }

   public int getHandGlowRadius() {
      return this.handGlow.getValue() ? Math.round(this.handGlowRadius.getValue()) : 0;
   }

   public float getHandGlowIntensity() {
      return this.handGlowIntensity.getValue();
   }

   public int getHandRgb() {
      Color c = this.handColor.getValue();
      return c.getRed() << 16 | c.getGreen() << 8 | c.getBlue();
   }

   public int getGlowRadius() {
      return this.glow.getValue() ? Math.round(this.glowRadius.getValue()) : 0;
   }

   public float getGlowIntensity() {
      return this.glowIntensity.getValue();
   }

   public float getFillTint() {
      return this.fillTint.getValue();
   }

   public float getFillAlpha() {
      return this.fillAlpha.getValue();
   }

   public boolean wantsOutlines() {
      ShadersModule.Mode m = this.mode.getValue();
      return m != null && m != ShadersModule.Mode.Off;
   }

   public boolean shouldShader(class_1297 entity) {
      ShadersModule.Mode m = this.mode.getValue();
      if (m == null || m == ShadersModule.Mode.Off) {
         return false;
      } else if (entity == null) {
         return false;
      } else if (mc.field_1724 != null && entity == mc.field_1724) {
         return false;
      } else if (entity instanceof class_742 p && !(entity instanceof class_746)) {
         boolean friend = Homovore.friendManager.isFriend(p);
         return friend
            ? this.friends.getValue() && this.inRange(entity, this.friendRange.getValue())
            : this.players.getValue() && this.inRange(entity, this.playerRange.getValue());
      } else if (entity instanceof class_1588) {
         return this.monsters.getValue() && this.inRange(entity, this.monsterRange.getValue());
      } else if (entity instanceof class_1429) {
         return this.animals.getValue() && this.inRange(entity, this.animalRange.getValue());
      } else if (entity instanceof class_1542) {
         return this.items.getValue() && this.inRange(entity, this.itemRange.getValue());
      } else if (entity instanceof class_1511) {
         return this.crystals.getValue() && this.inRange(entity, this.crystalRange.getValue());
      } else {
         return !(entity instanceof class_1676) ? false : this.projectiles.getValue() && this.inRange(entity, this.projectileRange.getValue());
      }
   }

   private boolean inRange(class_1297 entity, float range) {
      return mc.field_1724 == null || entity.method_5739(mc.field_1724) <= range;
   }

   public int getRgbFor(class_1297 entity) {
      Color c = this.colorFor(entity);
      return c.getRed() << 16 | c.getGreen() << 8 | c.getBlue();
   }

   private Color colorFor(class_1297 entity) {
      if (entity instanceof class_742 p && !(entity instanceof class_746)) {
         return Homovore.friendManager.isFriend(p) ? this.friendColor.getValue() : this.playerColor.getValue();
      } else if (entity instanceof class_1588) {
         return this.monsterColor.getValue();
      } else if (entity instanceof class_1429) {
         return this.animalColor.getValue();
      } else if (entity instanceof class_1542) {
         return this.itemColor.getValue();
      } else if (entity instanceof class_1511) {
         return this.crystalColor.getValue();
      } else {
         return entity instanceof class_1676 ? this.projectileColor.getValue() : this.playerColor.getValue();
      }
   }

   public enum Mode {
      Default,
      Off;
   }
}
