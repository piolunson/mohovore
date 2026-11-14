package dev.leonetic.v2.event.impl;

import dev.leonetic.v2.event.Event;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Identifier;

public class PlaySoundEvent extends Event {
   private final SoundInstance sound;

   public PlaySoundEvent(SoundInstance sound) {
      this.sound = sound;
   }

   public SoundInstance getSound() {
      return this.sound;
   }

   public Identifier getIdentifier() {
      return this.sound.getId();
   }
}
