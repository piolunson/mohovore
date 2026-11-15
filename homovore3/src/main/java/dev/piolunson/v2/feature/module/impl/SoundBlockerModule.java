package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.event.impl.PlaySoundEvent;
import dev.piolunson.v2.event.system.Subscribe;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.SoundSetting;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundBlockerModule extends Module {
   public final SoundSetting sounds = this.register(new SoundSetting("Sounds"));

   public SoundBlockerModule() {
      super("SoundBlocker", Category.RENDER, "Silences the sounds you pick.");
      this.sounds.describe("The sounds to silence.");
      this.sounds.onChange(this::stopBlocked);
   }

   @Override
   public void onEnable() {
      this.stopBlocked();
   }

   @Subscribe
   public void onPlaySound(PlaySoundEvent event) {
      if (this.isBlocked(event.getIdentifier())) {
         event.cancel();
      }
   }

   private boolean isBlocked(Identifier identifier) {
      SoundEvent sound = (SoundEvent)Registries.SOUND_EVENT.get(identifier);
      return sound != null && this.sounds.contains(sound);
   }

   private void stopBlocked() {
      if (this.isEnabled()) {
         SoundManager soundManager = mc.getSoundManager();
         if (soundManager != null) {
            for (SoundEvent sound : this.sounds.getValue()) {
               soundManager.stopSounds(sound.id(), null);
            }
         }
      }
   }
}
