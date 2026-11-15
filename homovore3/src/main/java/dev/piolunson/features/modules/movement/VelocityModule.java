package dev.piolunson.features.modules.movement;

import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import net.minecraft.client.MinecraftClient;

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

      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && mc.world != null ? mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox()).iterator().hasNext() : false;
   }
}
