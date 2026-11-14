package dev.leonetic.feature.module.impl;

import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.mixin.ClientboundEntityEventPacketAccessor;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1802;
import net.minecraft.class_2663;

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
      if (event.getPacket() instanceof class_2663 packet && packet.method_11470() == 35) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         int entityId = ((ClientboundEntityEventPacketAccessor)packet).homovore$getEntityId();
         if (snapshot.available() && snapshot.menuId() == 0 && entityId == snapshot.playerId()) {
            this.pending.set(null);
            if (snapshot.offhand().method_31574(class_1802.field_8288) && !snapshot.item(snapshot.selectedSlot()).method_31574(class_1802.field_8288)) {
               Result totem = InventoryUtil.find(class_1802.field_8288, InventoryUtil.MAIN_INVENTORY_SCOPE);
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
            if (snapshot.offhand().method_31574(class_1802.field_8288)) {
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

         if (!snapshot.offhand().method_31574(class_1802.field_8288)) {
            this.refill();
         }
      }
   }

   @Override
   public void onDisable() {
      this.pending.set(null);
   }

   private void refill() {
      Result totem = InventoryUtil.find(class_1802.field_8288, InventoryUtil.MAIN_INVENTORY_SCOPE);
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
