package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.event.impl.network.PacketEvent;
import dev.piolunson.v2.event.system.Subscribe;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.mixin.ClientboundEntityEventPacketAccessor;
import dev.piolunson.v2.util.inventory.InventorySnapshot;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.inventory.Result;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public final class AutoTotemModule extends Module {
   private static final byte TOTEM_POP_EVENT = 35;
   private static final long DISPATCH_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(200L);
   private final AtomicReference<AutoTotemModule.PendingRefill> pending = new AtomicReference<>();

   public AutoTotemModule() {
      super("AutoTotem", Category.SAFETY, "Ensures a totem is in your offhand.");
   }

   @Override
   public int getTickPriority() {
      return 2147483646;
   }

   @Subscribe(priority = Integer.MAX_VALUE)
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         int entityId = ((ClientboundEntityEventPacketAccessor)packet).homovore$getEntityId();
         if (snapshot.available() && snapshot.menuId() == 0 && entityId == snapshot.playerId()) {
            this.pending.set(null);
            if (snapshot.offhand().isOf(Items.TOTEM_OF_UNDYING) && !snapshot.item(snapshot.selectedSlot()).isOf(Items.TOTEM_OF_UNDYING)) {
               Result totem = InventoryUtil.find(Items.TOTEM_OF_UNDYING, InventoryUtil.MAIN_INVENTORY_SCOPE);
               if (totem.found()) {
                  AutoTotemModule.PendingRefill marker = AutoTotemModule.PendingRefill.dispatched(totem.slot());
                  if (this.pending.compareAndSet(null, marker)) {
                     InventoryUtil.refillConsumedOffhand(totem).whenComplete((applied, failure) -> {
                        if (failure == null && Boolean.TRUE.equals(applied)) {
                           this.pending.compareAndSet(marker, marker.applied(InventoryUtil.snapshot().version()));
                        } else {
                           this.pending.compareAndSet(marker, null);
                        }
                     });
                  }
               }
            }
         }
      }
   }

   @Override
   public void onTick() {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (snapshot.available() && snapshot.menuId() == 0) {
         AutoTotemModule.PendingRefill current = this.pending.get();
         if (current != null) {
            if (snapshot.offhand().isOf(Items.TOTEM_OF_UNDYING)) {
               this.pending.compareAndSet(current, null);
               return;
            }

            boolean lost = current.appliedAtVersion() > 0L
               ? snapshot.version() > current.appliedAtVersion()
               : System.nanoTime() - current.dispatchedAtNanos() >= DISPATCH_TIMEOUT_NANOS;
            if (!lost) {
               return;
            }

            if (!this.pending.compareAndSet(current, null)) {
               return;
            }
         }

         if (!snapshot.offhand().isOf(Items.TOTEM_OF_UNDYING)) {
            this.refill();
         }
      }
   }

   @Override
   public void onDisable() {
      this.pending.set(null);
   }

   private void refill() {
      Result totem = InventoryUtil.find(Items.TOTEM_OF_UNDYING, InventoryUtil.MAIN_INVENTORY_SCOPE);
      if (totem.found() && Boolean.TRUE.equals(InventoryUtil.swapToOffhand(totem).getNow(Boolean.FALSE))) {
         this.pending.set(AutoTotemModule.PendingRefill.dispatched(totem.slot()).applied(InventoryUtil.snapshot().version()));
      }
   }

   private record PendingRefill(int sourceSlot, long dispatchedAtNanos, long appliedAtVersion) {
      static AutoTotemModule.PendingRefill dispatched(int sourceSlot) {
         return new AutoTotemModule.PendingRefill(sourceSlot, System.nanoTime(), 0L);
      }

      AutoTotemModule.PendingRefill applied(long version) {
         return new AutoTotemModule.PendingRefill(this.sourceSlot, this.dispatchedAtNanos, version);
      }
   }
}
