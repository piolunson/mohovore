package dev.leonetic.feature.module.impl;

import dev.leonetic.event.impl.PlaySoundEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.SoundSetting;
import net.minecraft.class_1144;
import net.minecraft.class_2960;
import net.minecraft.class_3414;
import net.minecraft.class_7923;

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

   private boolean isBlocked(class_2960 identifier) {
      class_3414 sound = (class_3414)class_7923.field_41172.method_63535(identifier);
      return sound != null && this.sounds.contains(sound);
   }

   private void stopBlocked() {
      if (this.isEnabled()) {
         class_1144 soundManager = mc.method_1483();
         if (soundManager != null) {
            for (class_3414 sound : this.sounds.getValue()) {
               soundManager.method_4875(sound.comp_3319(), null);
            }
         }
      }
   }
}
