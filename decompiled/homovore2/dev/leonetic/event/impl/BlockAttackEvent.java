package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import net.minecraft.class_2338;
import net.minecraft.class_2350;

public final class BlockAttackEvent extends Event {
   private final class_2338 pos;
   private final class_2350 direction;
   private final boolean initial;

   public BlockAttackEvent(class_2338 pos, class_2350 direction, boolean initial) {
      this.pos = pos.method_10062();
      this.direction = direction;
      this.initial = initial;
   }

   public class_2338 pos() {
      return this.pos;
   }

   public class_2350 direction() {
      return this.direction;
   }

   public boolean initial() {
      return this.initial;
   }
}
