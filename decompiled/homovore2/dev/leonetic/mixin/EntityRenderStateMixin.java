package dev.leonetic.mixin;

import dev.leonetic.render.EntityRenderStateAccess;
import net.minecraft.class_10017;
import net.minecraft.class_1297;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(class_10017.class)
public class EntityRenderStateMixin implements EntityRenderStateAccess {
   @Unique
   private class_1297 homovore$entity;

   @Override
   public class_1297 homovore$getEntity() {
      return this.homovore$entity;
   }

   @Override
   public void homovore$setEntity(class_1297 entity) {
      this.homovore$entity = entity;
   }
}
