package dev.leonetic.mixin;

import dev.leonetic.util.network.EndpointOptimizer;
import java.util.Optional;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net/minecraft/client/gui/screen/multiplayer/ConnectScreen$1")
public abstract class ConnectScreenThreadMixin {
   @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AllowedAddressResolver;resolve(Lnet/minecraft/client/network/ServerAddress;)Ljava/util/Optional;"))
   private Optional<Address> homovore$optimizeEndpoint(AllowedAddressResolver resolver, ServerAddress requested) {
      return EndpointOptimizer.resolve(resolver, requested);
   }
}
