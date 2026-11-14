package dev.leonetic.feature.module.impl;

import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.RenderBlockEntityEvent;
import dev.leonetic.event.impl.render.RenderEntityEvent;
import dev.leonetic.event.impl.render.RenderFeatureEvent;
import dev.leonetic.event.impl.render.RenderParticleEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.RegistryKind;
import dev.leonetic.feature.setting.RegistrySetting;
import dev.leonetic.feature.setting.Setting;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;

public class NoRenderModule extends Module {
   private final Map<RenderFeatureEvent.Feature, Setting<Boolean>> features = new EnumMap<>(RenderFeatureEvent.Feature.class);
   public final Setting<Boolean> portalOverlay = this.feature("Portal Overlay", RenderFeatureEvent.Feature.PORTAL_OVERLAY, "Overlay");
   public final Setting<Boolean> spyglassOverlay = this.feature("Spyglass Overlay", RenderFeatureEvent.Feature.SPYGLASS_OVERLAY, "Overlay");
   public final Setting<Boolean> nausea = this.feature("Nausea", RenderFeatureEvent.Feature.NAUSEA, "Overlay");
   public final Setting<Boolean> pumpkinOverlay = this.feature("Pumpkin Overlay", RenderFeatureEvent.Feature.PUMPKIN_OVERLAY, "Overlay");
   public final Setting<Boolean> powderedSnowOverlay = this.feature("Powdered Snow Overlay", RenderFeatureEvent.Feature.POWDERED_SNOW_OVERLAY, "Overlay");
   public final Setting<Boolean> fireOverlay = this.feature("Fire Overlay", RenderFeatureEvent.Feature.FIRE_OVERLAY, "Overlay");
   public final Setting<Boolean> liquidOverlay = this.feature("Liquid Overlay", RenderFeatureEvent.Feature.LIQUID_OVERLAY, "Overlay");
   public final Setting<Boolean> inWallOverlay = this.feature("In Wall Overlay", RenderFeatureEvent.Feature.IN_WALL_OVERLAY, "Overlay");
   public final Setting<Boolean> vignette = this.feature("Vignette", RenderFeatureEvent.Feature.VIGNETTE, "Overlay");
   public final Setting<Boolean> guiBackground = this.feature("GUI Background", RenderFeatureEvent.Feature.GUI_BACKGROUND, "Overlay");
   public final Setting<Boolean> totemAnimation = this.feature("Totem Animation", RenderFeatureEvent.Feature.TOTEM_ANIMATION, "Overlay");
   public final Setting<Boolean> eatingParticles = this.feature("Eating Particles", RenderFeatureEvent.Feature.EATING_PARTICLES, "Overlay");
   public final Setting<Boolean> enchantmentGlint = this.feature("Enchantment Glint", RenderFeatureEvent.Feature.ENCHANTMENT_GLINT, "Overlay");
   public final Setting<Boolean> bossBar = this.feature("Boss Bar", RenderFeatureEvent.Feature.BOSS_BAR, "HUD");
   public final Setting<Boolean> scoreboard = this.feature("Scoreboard", RenderFeatureEvent.Feature.SCOREBOARD, "HUD");
   public final Setting<Boolean> crosshair = this.feature("Crosshair", RenderFeatureEvent.Feature.CROSSHAIR, "HUD");
   public final Setting<Boolean> title = this.feature("Title", RenderFeatureEvent.Feature.TITLE, "HUD");
   public final Setting<Boolean> heldItemName = this.feature("Held Item Name", RenderFeatureEvent.Feature.HELD_ITEM_NAME, "HUD");
   public final Setting<Boolean> obfuscation = this.feature("Obfuscation", RenderFeatureEvent.Feature.OBFUSCATION, "HUD")
      .describe("Reads text a server hides behind scrambling glyphs as the text itself.");
   public final Setting<Boolean> potionIcons = this.feature("Potion Icons", RenderFeatureEvent.Feature.POTION_ICONS, "HUD");
   public final Setting<Boolean> messageSignatureIndicator = this.feature(
      "Message Signature Indicator", RenderFeatureEvent.Feature.MESSAGE_SIGNATURE_INDICATOR, "HUD"
   );
   public final Setting<Boolean> weather = this.feature("Weather", RenderFeatureEvent.Feature.WEATHER, "World");
   public final Setting<Boolean> worldBorder = this.feature("World Border", RenderFeatureEvent.Feature.WORLD_BORDER, "World");
   public final Setting<Boolean> fog = this.feature("Fog", RenderFeatureEvent.Feature.FOG, "World");
   public final Setting<Boolean> barrierInvisibility = this.feature("Barrier Invisibility", RenderFeatureEvent.Feature.BARRIER_INVISIBILITY, "World")
      .describe("Draws barrier particles whether or not one is being held.");
   public final Setting<Boolean> blindness = this.feature("Blindness", RenderFeatureEvent.Feature.BLINDNESS, "World");
   public final Setting<Boolean> darkness = this.feature("Darkness", RenderFeatureEvent.Feature.DARKNESS, "World");
   public final Setting<Boolean> enchantTableBook = this.feature("Enchantment Table Book", RenderFeatureEvent.Feature.ENCHANTMENT_TABLE_BOOK, "World");
   public final Setting<Boolean> signText = this.feature("Sign Text", RenderFeatureEvent.Feature.SIGN_TEXT, "World");
   public final Setting<Boolean> blockBreakOverlay = this.feature("Block Break Overlay", RenderFeatureEvent.Feature.BLOCK_BREAK_OVERLAY, "World");
   public final Setting<Boolean> beaconBeams = this.feature("Beacon Beams", RenderFeatureEvent.Feature.BEACON_BEAMS, "World");
   public final Setting<Boolean> mapMarkers = this.feature("Map Markers", RenderFeatureEvent.Feature.MAP_MARKERS, "World");
   public final Setting<Boolean> mapContents = this.feature("Map Contents", RenderFeatureEvent.Feature.MAP_CONTENTS, "World");
   public final Setting<Boolean> caveCulling = this.feature("Cave Culling", RenderFeatureEvent.Feature.CAVE_CULLING, "World")
      .describe("Builds chunks without the graph that hides what is behind a solid block.")
      .onChange(NoRenderModule::rebuildChunks);
   public final Setting<Boolean> textureRotations = this.feature("Texture Rotations", RenderFeatureEvent.Feature.TEXTURE_ROTATIONS, "World")
      .describe("Puts every block's model straight, so grass and flowers stop being scattered.")
      .onChange(NoRenderModule::rebuildChunks);
   public final Setting<NoRenderModule.BannerMode> banners = this.mode("Banners", NoRenderModule.BannerMode.NONE).page("World");
   public final Setting<Boolean> fireworkExplosions = this.bool("Firework Explosions", true).page("World");
   public final RegistrySetting<ParticleType<?>> particles = this.register(new RegistrySetting<>("Particles", RegistryKind.PARTICLE, List.of()));
   public final RegistrySetting<BlockEntityType<?>> blockEntities = this.register(new RegistrySetting<>("Block Entities", RegistryKind.BLOCK_ENTITY, List.of()));
   public final RegistrySetting<EntityType<?>> entities = this.register(new RegistrySetting<>("Entities", RegistryKind.ENTITY, List.of()));
   public final Setting<Boolean> dropSpawnPackets = this.bool("Drop Spawn Packets", false)
      .page("Entity")
      .describe("Never adds the picked entities to the world, rather than only hiding them.");
   public final Setting<Boolean> fallingBlocks = this.bool("Falling Blocks", false).page("Entity");
   public final Setting<Boolean> armor = this.feature("Armor", RenderFeatureEvent.Feature.ARMOR, "Entity");
   public final Setting<Boolean> invisibility = this.feature("Invisibility", RenderFeatureEvent.Feature.INVISIBILITY, "Entity")
      .describe("Draws invisible entities anyway.");
   public final Setting<Boolean> glowing = this.feature("Glowing", RenderFeatureEvent.Feature.GLOWING, "Entity");
   public final Setting<Boolean> spawnerEntities = this.feature("Spawner Entities", RenderFeatureEvent.Feature.SPAWNER_ENTITIES, "Entity");
   public final Setting<Boolean> deadEntities = this.feature("Dead Entities", RenderFeatureEvent.Feature.DEAD_ENTITIES, "Entity");
   public final Setting<Boolean> hurt = this.feature("Hurt", RenderFeatureEvent.Feature.HURT, "Entity")
      .describe("Leaves the red wash off an entity that has just been hit.");
   public final Setting<Boolean> nametags = this.feature("Nametags", RenderFeatureEvent.Feature.NAMETAGS, "Entity");

   public NoRenderModule() {
      super("NoRender", Category.RENDER, "Leaves out overlays, effects and entities you would rather not see.");
      this.particles.page("World");
      this.blockEntities.page("World");
      this.entities.page("Entity");
   }

   @Override
   public void onEnable() {
      this.rebuildChunksIfBaked();
   }

   @Override
   public void onDisable() {
      this.rebuildChunksIfBaked();
   }

   @Subscribe
   public void onRenderFeature(RenderFeatureEvent event) {
      if (this.isSuppressed(event.getFeature())) {
         event.cancel();
      }
   }

   @Subscribe
   public void onRenderParticle(RenderParticleEvent event) {
      if (this.particles.contains(event.getType())
         || this.weather.getValue() && event.getType() == ParticleTypes.RAIN
         || this.fireworkExplosions.getValue() && event.getType() == ParticleTypes.FIREWORK) {
         event.cancel();
      }
   }

   @Subscribe
   public void onRenderEntity(RenderEntityEvent event) {
      if (this.entities.contains(event.getEntity().getType()) || this.fallingBlocks.getValue() && event.getEntity() instanceof FallingBlockEntity) {
         event.cancel();
      }
   }

   @Subscribe
   public void onRenderBlockEntity(RenderBlockEntityEvent event) {
      if (this.blockEntities.contains(event.getType())) {
         event.cancel();
      }
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (this.dropSpawnPackets.getValue() && event.getPacket() instanceof EntitySpawnS2CPacket packet && this.entities.contains(packet.getEntityType())) {
         event.cancel();
      }
   }

   private boolean isSuppressed(RenderFeatureEvent.Feature feature) {
      return switch (feature) {
         case BANNERS -> this.banners.getValue() == NoRenderModule.BannerMode.NONE;
         case BANNER_PATTERNS -> this.banners.getValue() != NoRenderModule.BannerMode.EVERYTHING;
         default -> {
            Setting<Boolean> setting = this.features.get(feature);
            yield setting != null && setting.getValue();
         }
      };
   }

   private Setting<Boolean> feature(String name, RenderFeatureEvent.Feature feature, String page) {
      Setting<Boolean> setting = this.bool(name, false).page(page);
      this.features.put(feature, setting);
      return setting;
   }

   private void rebuildChunksIfBaked() {
      if (this.caveCulling.getValue() || this.textureRotations.getValue()) {
         rebuildChunks();
      }
   }

   private static void rebuildChunks() {
      if (mc.world != null) {
         mc.worldRenderer.reload();
      }
   }

   public enum BannerMode {
      EVERYTHING,
      PILLAR,
      NONE;
   }
}
