package dev.leonetic.mixin;

import dev.leonetic.util.network.EndpointOptimizer;
import java.util.Optional;
import net.minecraft.class_6368;
import net.minecraft.class_6370;
import net.minecraft.class_639;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net/minecraft/class_412$1")
public abstract class ConnectScreenThreadMixin {
   @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_6370;method_36907(Lnet/minecraft/class_639;)Ljava/util/Optional;"))
   private Optional<class_6368> homovore$optimizeEndpoint(class_6370 resolver, class_639 requested) {
      return EndpointOptimizer.resolve(resolver, requested);
   }
}
