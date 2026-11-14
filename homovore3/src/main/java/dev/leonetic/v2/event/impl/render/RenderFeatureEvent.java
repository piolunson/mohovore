package dev.leonetic.v2.event.impl.render;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.Event;

public class RenderFeatureEvent extends Event {
   private final RenderFeatureEvent.Feature feature;

   public RenderFeatureEvent(RenderFeatureEvent.Feature feature) {
      this.feature = feature;
   }

   public RenderFeatureEvent.Feature getFeature() {
      return this.feature;
   }

   public static boolean isSuppressed(RenderFeatureEvent.Feature feature) {
      return Homovore2Client.EVENT_BUS.hasListeners(RenderFeatureEvent.class) && Homovore2Client.EVENT_BUS.post(new RenderFeatureEvent(feature));
   }

   public enum Feature {
      PORTAL_OVERLAY,
      SPYGLASS_OVERLAY,
      NAUSEA,
      PUMPKIN_OVERLAY,
      POWDERED_SNOW_OVERLAY,
      FIRE_OVERLAY,
      LIQUID_OVERLAY,
      IN_WALL_OVERLAY,
      VIGNETTE,
      GUI_BACKGROUND,
      TOTEM_ANIMATION,
      EATING_PARTICLES,
      ENCHANTMENT_GLINT,
      BOSS_BAR,
      SCOREBOARD,
      CROSSHAIR,
      TITLE,
      HELD_ITEM_NAME,
      OBFUSCATION,
      POTION_ICONS,
      MESSAGE_SIGNATURE_INDICATOR,
      WEATHER,
      WORLD_BORDER,
      FOG,
      TEXTURE_ROTATIONS,
      BARRIER_INVISIBILITY,
      BLINDNESS,
      DARKNESS,
      ENCHANTMENT_TABLE_BOOK,
      SIGN_TEXT,
      BLOCK_BREAK_OVERLAY,
      BEACON_BEAMS,
      CAVE_CULLING,
      MAP_MARKERS,
      MAP_CONTENTS,
      BANNERS,
      BANNER_PATTERNS,
      ARMOR,
      INVISIBILITY,
      GLOWING,
      SPAWNER_ENTITIES,
      DEAD_ENTITIES,
      HURT,
      NAMETAGS;
   }
}
