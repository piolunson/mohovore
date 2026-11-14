package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.ClickGuiSoundEvent;
import dev.leonetic.event.impl.PlaySoundEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.ListSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.sound.CustomSoundPlayer;
import dev.leonetic.util.world.VisualRangeTracker;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class CustomSoundsModule extends Module {
   private static final int XP_SPLASH_EVENT = 2002;
   private static final int XP_SPLASH_COLOR = -13083194;
   private static final long REMOVED_CRYSTAL_GRACE_MS = 2000L;
   private static final long POSITION_TRIGGER_GRACE_MS = 2000L;
   private static final double POSITION_MATCH_DISTANCE_SQUARED = 9.0;
   public final ListSetting crystalExplosion = this.sound("Crystal Explosion", "Default", "Combat");
   public final ListSetting totemPop = this.sound("Totem Pop", "Default", "Combat");
   public final ListSetting playerDeath = this.sound("Player Death", "Default", "Combat");
   public final ListSetting xpSplash = this.sound("XP Splash", "Default", "World");
   public final ListSetting visualRange = this.sound("Player Visual Range", "None", "World");
   public final Setting<Boolean> ignoreFriends = this.bool("Ignore Friends", false)
      .describe("Keeps a friend coming into range from playing the visual range sound.")
      .visibleWhen(this::usesVisualRange)
      .page("World");
   public final Setting<Boolean> gearedOnly = this.bool("Geared Only", false)
      .describe("Only plays for players in a full set of diamond or netherite armour.")
      .visibleWhen(this::usesVisualRange)
      .page("World");
   public final ListSetting clickGuiPress = this.sound("ClickGUI Press", "Default", "ClickGUI");
   public final ListSetting clickGuiHover = this.sound("ClickGUI Hover", "None", "ClickGUI");
   private final Map<Integer, CustomSoundsModule.CrystalSnapshot> crystals = new ConcurrentHashMap<>();
   private final ConcurrentLinkedQueue<CustomSoundsModule.LocatedTrigger> crystalExplosions = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<CustomSoundsModule.LocatedTrigger> xpSplashes = new ConcurrentLinkedQueue<>();
   private final VisualRangeTracker visualRangeTracker = new VisualRangeTracker();
   private ClientWorld level;

   public CustomSoundsModule() {
      super("CustomSounds", Category.EXTRA, "Replaces event sounds with OGG, MP3 or WAV files from homovore/sounds.");
      CustomSoundPlayer.rescanFolder();
   }

   private ListSetting sound(String name, String defaultValue, String page) {
      ListSetting setting = this.list(name, defaultValue, CustomSoundPlayer::names).refreshWith(CustomSoundPlayer::rescanFolder);
      setting.page(page);
      setting.describe("Default keeps vanilla audio; None silences it; a file replaces it.");
      return setting;
   }

   @Override
   public void onEnable() {
      this.resetWorldTracking();
      this.seedCurrentWorld();
   }

   @Override
   public void onDisable() {
      this.resetWorldTracking();
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      long now = System.currentTimeMillis();
      if (event.getPacket() instanceof EntitySpawnS2CPacket packet && packet.getEntityType() == EntityType.END_CRYSTAL) {
         this.crystals.put(packet.getEntityId(), new CustomSoundsModule.CrystalSnapshot(new Vec3d(packet.getX(), packet.getY(), packet.getZ()), 0L));
      } else if (event.getPacket() instanceof EntitiesDestroyS2CPacket packet) {
         IntListIterator var10 = packet.getEntityIds().iterator();

         while (var10.hasNext()) {
            int id = var10.next();
            this.crystals.computeIfPresent(id, (ignored, crystal) -> new CustomSoundsModule.CrystalSnapshot(crystal.position(), now));
         }
      } else if (event.getPacket() instanceof ExplosionS2CPacket packet) {
         if (this.consumeCrystal(packet.center())) {
            this.crystalExplosions.add(new CustomSoundsModule.LocatedTrigger(packet.center(), now + 2000L));
         }
      } else if (event.getPacket() instanceof WorldEventS2CPacket packet && packet.getEventId() == 2002 && packet.getData() == -13083194) {
         BlockPos pos = packet.getPos();
         this.xpSplashes.add(new CustomSoundsModule.LocatedTrigger(Vec3d.ofCenter(pos), now + 2000L));
      }
   }

   @Subscribe(priority = 1000)
   public void onPlaySound(PlaySoundEvent event) {
      Identifier sound = event.getIdentifier();
      if (sound.equals(SoundEvents.ITEM_TOTEM_USE.id())) {
         this.replace(event, this.totemPop);
      } else if (sound.equals(SoundEvents.ENTITY_PLAYER_DEATH.id())) {
         this.replace(event, this.playerDeath);
      } else if (sound.equals(((SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value()).id()) && this.consumeLocated(this.crystalExplosions, event.getSound())) {
         this.replace(event, this.crystalExplosion);
      } else if (sound.equals(SoundEvents.ENTITY_SPLASH_POTION_BREAK.id()) && this.consumeLocated(this.xpSplashes, event.getSound())) {
         this.replace(event, this.xpSplash);
      }
   }

   @Subscribe(priority = 1000)
   public void onClickGuiSound(ClickGuiSoundEvent event) {
      ListSetting setting = event.getType() == ClickGuiSoundEvent.Type.PRESS ? this.clickGuiPress : this.clickGuiHover;
      String selection = setting.getValue();
      boolean custom = CustomSoundPlayer.isCustom(selection);
      if (event.getType() == ClickGuiSoundEvent.Type.PRESS && (CustomSoundPlayer.isNone(selection) || custom)) {
         event.cancel();
      }

      if (custom) {
         CustomSoundPlayer.play(selection, SoundCategory.MASTER);
      }
   }

   @Override
   public void onTick() {
      long now = System.currentTimeMillis();
      this.crystals.entrySet().removeIf(entry -> entry.getValue().removedAt() > 0L && now - entry.getValue().removedAt() > 2000L);
      this.crystalExplosions.removeIf(trigger -> trigger.expiresAt() < now);
      this.xpSplashes.removeIf(trigger -> trigger.expiresAt() < now);
      if (mc.world == null || mc.player == null) {
         this.resetWorldTracking();
      } else if (mc.world != this.level) {
         this.resetWorldTracking();
         this.seedCurrentWorld();
      } else if (!this.usesVisualRange()) {
         if (!this.visualRangeTracker.isEmpty()) {
            this.visualRangeTracker.forget();
         }
      } else {
         this.visualRangeTracker
            .tick(
               this.gearedOnly.getValue(),
               player -> !this.ignoreFriends.getValue() || !Homovore2Client.friends().isFriend(player),
               player -> CustomSoundPlayer.play(this.visualRange.getValue(), SoundCategory.PLAYERS)
            );
      }
   }

   private void replace(PlaySoundEvent event, ListSetting setting) {
      String selection = setting.getValue();
      if (!CustomSoundPlayer.isDefault(selection)) {
         boolean custom = CustomSoundPlayer.isCustom(selection);
         if (CustomSoundPlayer.isNone(selection) || custom) {
            event.cancel();
         }

         if (custom) {
            CustomSoundPlayer.play(selection, event.getSound());
         }
      }
   }

   private boolean consumeCrystal(Vec3d center) {
      Integer nearestId = null;
      CustomSoundsModule.CrystalSnapshot nearest = null;
      double nearestDistance = 9.0;

      for (Entry<Integer, CustomSoundsModule.CrystalSnapshot> entry : this.crystals.entrySet()) {
         double distance = entry.getValue().position().squaredDistanceTo(center);
         if (distance <= nearestDistance) {
            nearestDistance = distance;
            nearestId = entry.getKey();
            nearest = entry.getValue();
         }
      }

      return nearestId != null && this.crystals.remove(nearestId, nearest);
   }

   private boolean consumeLocated(ConcurrentLinkedQueue<CustomSoundsModule.LocatedTrigger> triggers, SoundInstance sound) {
      long now = System.currentTimeMillis();
      Vec3d position = new Vec3d(sound.getX(), sound.getY(), sound.getZ());

      for (CustomSoundsModule.LocatedTrigger trigger : triggers) {
         if (trigger.expiresAt() < now) {
            triggers.remove(trigger);
         } else if (trigger.position().squaredDistanceTo(position) <= 9.0 && triggers.remove(trigger)) {
            return true;
         }
      }

      return false;
   }

   private void seedCurrentWorld() {
      this.level = mc.world;
      if (this.level != null && mc.player != null) {
         for (Entity entity : this.level.getEntities()) {
            if (entity.getType() == EntityType.END_CRYSTAL) {
               this.crystals.put(entity.getId(), new CustomSoundsModule.CrystalSnapshot(entity.getEntityPos(), 0L));
            }
         }
      }
   }

   private void resetWorldTracking() {
      this.crystals.clear();
      this.crystalExplosions.clear();
      this.xpSplashes.clear();
      this.visualRangeTracker.forget();
      this.level = null;
   }

   private boolean usesVisualRange() {
      return CustomSoundPlayer.isCustom(this.visualRange.getValue());
   }

   @Override
   public boolean isPersonal() {
      return true;
   }

   private record CrystalSnapshot(Vec3d position, long removedAt) {
   }

   private record LocatedTrigger(Vec3d position, long expiresAt) {
   }
}
