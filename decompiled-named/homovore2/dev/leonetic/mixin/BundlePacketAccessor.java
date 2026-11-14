package dev.leonetic.mixin;

import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BundlePacket.class)
public interface BundlePacketAccessor<T extends PacketListener> {
   @Mutable
   @Accessor("packets")
   void homovore$setPackets(Iterable<Packet<? super T>> var1);
}
