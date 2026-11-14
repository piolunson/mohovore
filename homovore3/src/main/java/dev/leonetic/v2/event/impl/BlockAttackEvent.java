package dev.leonetic.v2.event.impl;

import dev.leonetic.v2.event.Event;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class BlockAttackEvent extends Event {
   private final BlockPos pos;
   private final Direction direction;
   private final boolean initial;

   public BlockAttackEvent(BlockPos pos, Direction direction, boolean initial) {
      this.pos = pos.toImmutable();
      this.direction = direction;
      this.initial = initial;
   }

   public BlockPos pos() {
      return this.pos;
   }

   public Direction direction() {
      return this.direction;
   }

   public boolean initial() {
      return this.initial;
   }
}
