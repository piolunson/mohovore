package dev.leonetic.util.inventory;

import dev.leonetic.Homovore2;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_310;
import net.minecraft.class_746;

public final class ContainerClicks {
   private ContainerClicks() {
      throw new AssertionError();
   }

   public static void dispatch(Runnable work) {
      Objects.requireNonNull(work, "work");
      class_310.method_1551().execute(work);
   }

   public static boolean apply(int containerId, int menuSlot, int button, class_1713 input) {
      Objects.requireNonNull(input, "input");
      class_310 client = class_310.method_1551();
      if (!client.method_18854()) {
         throw new IllegalStateException("Container clicks must be applied on the client thread");
      }

      class_746 player = client.field_1724;
      if (player != null && client.field_1761 != null) {
         class_1703 menu = player.field_7512;
         if (menu.field_7763 == containerId && menuSlot >= 0 && menuSlot < menu.field_7761.size()) {
            Homovore2.LOGGER
               .info(
                  "[Inventory] click apply container={} state={} input={} menuSlot={} button={}",
                  new Object[]{containerId, menu.method_37421(), input, menuSlot, button}
               );

            try {
               client.field_1761.method_2906(containerId, menuSlot, button, input, player);
               return true;
            } finally {
               InventoryUtil.capture(client);
            }
         } else {
            Homovore2.LOGGER
               .info(
                  "[Inventory] click dropped container={} expected={} menuSlot={} slots={}",
                  new Object[]{menu.field_7763, containerId, menuSlot, menu.field_7761.size()}
               );
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean cursorEmpty() {
      class_746 player = class_310.method_1551().field_1724;
      return player != null && player.field_7512.method_34255().method_7960();
   }

   public static CompletableFuture<Boolean> submitPlan(int containerId, int stateId, List<ContainerClicks.PlannedClick> clicks, Runnable onApplied) {
      Objects.requireNonNull(clicks, "clicks");
      List<ContainerClicks.PlannedClick> plan = List.copyOf(clicks);
      if (plan.isEmpty()) {
         return CompletableFuture.completedFuture(false);
      }

      CompletableFuture<Boolean> applied = new CompletableFuture<>();

      try {
         NetworkThreadExecutor.<Object>submit(context -> {
            int cost = plan.size();
            if (!context.limits().canSend(PacketLimiter.Bucket.CLICK_SLOT, cost)) {
               Homovore2.LOGGER.info("[Inventory] click plan rejected by packet budget required={}", cost);
               applied.complete(false);
               return null;
            } else {
               long observedBefore = context.limits().containerClicksObserved();
               dispatch(() -> {
                  try {
                     applied.complete(applyPlan(containerId, stateId, plan, observedBefore, onApplied));
                  } catch (Throwable throwable) {
                     applied.completeExceptionally(throwable);
                  }
               });
               return null;
            }
         }).whenComplete((ignored, failure) -> {
            if (failure != null) {
               applied.completeExceptionally(failure);
            }
         });
         return applied;
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }
   }

   private static boolean applyPlan(int containerId, int stateId, List<ContainerClicks.PlannedClick> plan, long observedBefore, Runnable onApplied) {
      class_310 client = class_310.method_1551();
      class_746 player = client.field_1724;
      if (player != null && client.field_1761 != null) {
         class_1703 menu = player.field_7512;
         if (menu.field_7763 == containerId && menu.method_37421() == stateId && cursorEmpty()) {
            int applied = 0;

            try {
               for (ContainerClicks.PlannedClick click : plan) {
                  if (!apply(containerId, click.menuSlot(), click.button(), click.input())) {
                     break;
                  }

                  applied++;
               }

               boolean complete = applied == plan.size() && cursorEmpty();
               if (!cursorEmpty()) {
                  Homovore2.LOGGER.warn("[Inventory] click plan left an item on the cursor");
               }

               if (complete && onApplied != null) {
                  onApplied.run();
               }

               return complete;
            } finally {
               NetworkThreadExecutor.reconcileDispatchedClicks(observedBefore, applied);
            }
         } else {
            Homovore2.LOGGER
               .info(
                  "[Inventory] stale click plan dropped container={} expected={} state={} expectedState={} cursorEmpty={}",
                  new Object[]{menu.field_7763, containerId, menu.method_37421(), stateId, cursorEmpty()}
               );
            return false;
         }
      } else {
         return false;
      }
   }

   public record PlannedClick(int menuSlot, int button, class_1713 input) {
      public PlannedClick(int menuSlot, int button, class_1713 input) {
         Objects.requireNonNull(input, "input");
         if (menuSlot < 0 || menuSlot > 32767) {
            throw new IllegalArgumentException("Menu slot is outside the packet range: " + menuSlot);
         }

         if (button >= -128 && button <= 127) {
            this.menuSlot = menuSlot;
            this.button = button;
            this.input = input;
         } else {
            throw new IllegalArgumentException("Click button is outside the packet range: " + button);
         }
      }
   }
}
