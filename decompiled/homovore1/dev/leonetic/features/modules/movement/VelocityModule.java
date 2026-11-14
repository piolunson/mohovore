package dev.leonetic.features.modules.movement;

import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.class_310;

public class VelocityModule extends Module {
   public final Setting<Boolean> phaseOnly = this.bool("PhaseOnly", true);
   public final Setting<Boolean> knockback = this.bool("Knockback", true);
   public final Setting<Boolean> explosions = this.bool("Explosions", false);
   public final Setting<Boolean> entityPush = this.bool("EntityPush", true);
   public final Setting<Boolean> blockPush = this.bool("BlockPush", true);

   public VelocityModule() {
      super("Velocity", "Cancels knockback, explosions, and entity/block push.", Module.Category.MOVEMENT);
   }

   public boolean shouldCancelKnockback() {
      return this.isEnabled() && this.knockback.getValue() && this.phaseConditionMet();
   }

   public boolean phaseConditionMet() {
      if (!this.phaseOnly.getValue()) {
         return true;
      }

      class_310 mc = class_310.method_1551();
      return mc.field_1724 != null && mc.field_1687 != null
         ? mc.field_1687.method_20812(mc.field_1724, mc.field_1724.method_5829()).iterator().hasNext()
         : false;
   }
}
