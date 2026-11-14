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
import net.minecraft.class_1113;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2604;
import net.minecraft.class_2664;
import net.minecraft.class_2673;
import net.minecraft.class_2716;
import net.minecraft.class_2960;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_3419;
import net.minecraft.class_638;

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
   private class_638 level;

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
      if (event.getPacket() instanceof class_2604 packet && packet.method_11169() == class_1299.field_6110) {
         this.crystals
            .put(
               packet.method_11167(),
               new CustomSoundsModule.CrystalSnapshot(new class_243(packet.method_11175(), packet.method_11174(), packet.method_11176()), 0L)
            );
      } else if (event.getPacket() instanceof class_2716 packet) {
         IntListIterator var10 = packet.method_36548().iterator();

         while (var10.hasNext()) {
            int id = var10.next();
            this.crystals.computeIfPresent(id, (ignored, crystal) -> new CustomSoundsModule.CrystalSnapshot(crystal.position(), now));
         }
      } else if (event.getPacket() instanceof class_2664 packet) {
         if (this.consumeCrystal(packet.comp_2883())) {
            this.crystalExplosions.add(new CustomSoundsModule.LocatedTrigger(packet.comp_2883(), now + 2000L));
         }
      } else if (event.getPacket() instanceof class_2673 packet && packet.method_11532() == 2002 && packet.method_11534() == -13083194) {
         class_2338 pos = packet.method_11531();
         this.xpSplashes.add(new CustomSoundsModule.LocatedTrigger(class_243.method_24953(pos), now + 2000L));
      }
   }

   @Subscribe(priority = 1000)
   public void onPlaySound(PlaySoundEvent event) {
      class_2960 sound = event.getIdentifier();
      if (sound.equals(class_3417.field_14931.comp_3319())) {
         this.replace(event, this.totemPop);
      } else if (sound.equals(class_3417.field_14904.comp_3319())) {
         this.replace(event, this.playerDeath);
      } else if (sound.equals(((class_3414)class_3417.field_15152.comp_349()).comp_3319()) && this.consumeLocated(this.crystalExplosions, event.getSound())) {
         this.replace(event, this.crystalExplosion);
      } else if (sound.equals(class_3417.field_14839.comp_3319()) && this.consumeLocated(this.xpSplashes, event.getSound())) {
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
         CustomSoundPlayer.play(selection, class_3419.field_15250);
      }
   }

   @Override
   public void onTick() {
      long now = System.currentTimeMillis();
      this.crystals.entrySet().removeIf(entry -> entry.getValue().removedAt() > 0L && now - entry.getValue().removedAt() > 2000L);
      this.crystalExplosions.removeIf(trigger -> trigger.expiresAt() < now);
      this.xpSplashes.removeIf(trigger -> trigger.expiresAt() < now);
      if (mc.field_1687 == null || mc.field_1724 == null) {
         this.resetWorldTracking();
      } else if (mc.field_1687 != this.level) {
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
               player -> CustomSoundPlayer.play(this.visualRange.getValue(), class_3419.field_15248)
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

   private boolean consumeCrystal(class_243 center) {
      Integer nearestId = null;
      CustomSoundsModule.CrystalSnapshot nearest = null;
      double nearestDistance = 9.0;

      for (Entry<Integer, CustomSoundsModule.CrystalSnapshot> entry : this.crystals.entrySet()) {
         double distance = entry.getValue().position().method_1025(center);
         if (distance <= nearestDistance) {
            nearestDistance = distance;
            nearestId = entry.getKey();
            nearest = entry.getValue();
         }
      }

      return nearestId != null && this.crystals.remove(nearestId, nearest);
   }

   private boolean consumeLocated(ConcurrentLinkedQueue<CustomSoundsModule.LocatedTrigger> triggers, class_1113 sound) {
      long now = System.currentTimeMillis();
      class_243 position = new class_243(sound.method_4784(), sound.method_4779(), sound.method_4778());

      for (CustomSoundsModule.LocatedTrigger trigger : triggers) {
         if (trigger.expiresAt() < now) {
            triggers.remove(trigger);
         } else if (trigger.position().method_1025(position) <= 9.0 && triggers.remove(trigger)) {
            return true;
         }
      }

      return false;
   }

   private void seedCurrentWorld() {
      this.level = mc.field_1687;
      if (this.level != null && mc.field_1724 != null) {
         for (class_1297 entity : this.level.method_18112()) {
            if (entity.method_5864() == class_1299.field_6110) {
               this.crystals.put(entity.method_5628(), new CustomSoundsModule.CrystalSnapshot(entity.method_73189(), 0L));
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

   private record CrystalSnapshot(class_243 position, long removedAt) {
   }

   private record LocatedTrigger(class_243 position, long expiresAt) {
   }
}
