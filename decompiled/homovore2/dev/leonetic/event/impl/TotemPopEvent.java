package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import net.minecraft.class_1657;
import net.minecraft.class_310;

public class TotemPopEvent extends Event {
   private final class_1657 player;
   private final int pops;

   public TotemPopEvent(class_1657 player, int pops) {
      this.player = player;
      this.pops = pops;
   }

   public class_1657 getPlayer() {
      return this.player;
   }

   public int getPops() {
      return this.pops;
   }

   public boolean isSelf() {
      return this.player == class_310.method_1551().field_1724;
   }
}
