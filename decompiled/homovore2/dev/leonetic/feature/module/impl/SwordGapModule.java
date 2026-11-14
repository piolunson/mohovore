package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.inventory.SwapManager;
import dev.leonetic.util.network.NetworkThreadExecutor;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1802;
import net.minecraft.class_3489;

public final class SwordGapModule extends Module {
   private static final Set<ResultType> HOTBAR_ONLY = Set.of(ResultType.HOTBAR);
   private final AtomicReference<SwapManager.MainhandLease> lease = new AtomicReference<>();
   private final AtomicBoolean acquisitionPending = new AtomicBoolean();
   private final AtomicBoolean wantsLease = new AtomicBoolean();
   private final AtomicLong requestGeneration = new AtomicLong();

   public SwordGapModule() {
      super("SwordGap", Category.COMBAT, "Switches from a sword to an enchanted golden apple while use is held.");
   }

   @Override
   public void onTick() {
      SwapManager.MainhandLease current = this.lease.get();
      if (current != null && !current.isActive()) {
         this.lease.compareAndSet(current, null);
      }

      if (mc.field_1724 != null && mc.field_1690.field_1904.method_1434()) {
         this.wantsLease.set(true);
         if (this.lease.get() == null && !this.acquisitionPending.get() && mc.field_1724.method_6047().method_31573(class_3489.field_42611)) {
            Result gapple = InventoryUtil.find(class_1802.field_8367, HOTBAR_ONLY);
            if (gapple.found() && this.acquisitionPending.compareAndSet(false, true)) {
               long generation = this.requestGeneration.incrementAndGet();

               try {
                  NetworkThreadExecutor.swaps()
                     .leaseMainhand(gapple)
                     .whenComplete(
                        (acquired, failure) -> {
                           this.acquisitionPending.set(false);
                           if (acquired != null
                              && (!this.wantsLease.get() || this.requestGeneration.get() != generation || !this.lease.compareAndSet(null, acquired))) {
                              acquired.close();
                           }
                        }
                     );
               } catch (RuntimeException exception) {
                  this.acquisitionPending.set(false);
               }
            }
         }
      } else {
         this.wantsLease.set(false);
         this.requestGeneration.incrementAndGet();
         this.releaseLease();
      }
   }

   @Override
   public void onDisable() {
      this.wantsLease.set(false);
      this.requestGeneration.incrementAndGet();
      this.releaseLease();
   }

   private void releaseLease() {
      SwapManager.MainhandLease current = this.lease.getAndSet(null);
      if (current != null) {
         current.close();
      }
   }
}
