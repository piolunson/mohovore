package dev.leonetic.event.impl.render;

import dev.leonetic.event.Event;
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
