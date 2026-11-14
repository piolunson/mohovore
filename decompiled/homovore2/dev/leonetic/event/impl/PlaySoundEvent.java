package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import net.minecraft.class_1113;
import net.minecraft.class_2960;

public class PlaySoundEvent extends Event {
   private final class_1113 sound;

   public PlaySoundEvent(class_1113 sound) {
      this.sound = sound;
   }

   public class_1113 getSound() {
      return this.sound;
   }

   public class_2960 getIdentifier() {
      return this.sound.method_4775();
   }
}
