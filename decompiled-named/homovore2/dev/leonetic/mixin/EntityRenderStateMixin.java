package dev.leonetic.mixin;

import dev.leonetic.render.EntityRenderStateAccess;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateAccess {
   @Unique
   private Entity homovore$entity;

   @Override
   public Entity homovore$getEntity() {
      return this.homovore$entity;
   }

   @Override
   public void homovore$setEntity(Entity entity) {
      this.homovore$entity = entity;
   }
}
