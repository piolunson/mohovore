package dev.piolunson.v2.event.impl.render;

import dev.piolunson.v2.event.Event;
import net.minecraft.block.entity.BlockEntityType;

public class RenderBlockEntityEvent extends Event {
   private final BlockEntityType<?> type;

   public RenderBlockEntityEvent(BlockEntityType<?> type) {
      this.type = type;
   }

   public BlockEntityType<?> getType() {
      return this.type;
   }
}
