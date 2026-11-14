package dev.leonetic.v2.util.inventory;

import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import dev.leonetic.v2.util.network.PacketLimiter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class ContainerClicks {
   private ContainerClicks() {
      throw new AssertionError();
   }

   public static void dispatch(Runnable work) {
      Objects.requireNonNull(work, "work");
      MinecraftClient.getInstance().execute(work);
   }

   public static boolean apply(int containerId, int menuSlot, int button, SlotActionType input) {
      Objects.requireNonNull(input, "input");
      MinecraftClient client = MinecraftClient.getInstance();
      if (!client.isOnThread()) {
         throw new IllegalStateException("Container clicks must be applied on the client thread");
      }

      ClientPlayerEntity player = client.player;
      if (player != null && client.interactionManager != null) {
         ScreenHandler menu = player.currentScreenHandler;
         if (menu.syncId == containerId && menuSlot >= 0 && menuSlot < menu.slots.size()) {
            Homovore2.LOGGER
               .info(
                  "[Inventory] click apply container={} state={} input={} menuSlot={} button={}",
                  new Object[]{containerId, menu.getRevision(), input, menuSlot, button}
               );

            try {
               client.interactionManager.clickSlot(containerId, menuSlot, button, input, player);
               return true;
            } finally {
               InventoryUtil.capture(client);
            }
         } else {
            Homovore2.LOGGER
               .info(
                  "[Inventory] click dropped container={} expected={} menuSlot={} slots={}",
                  new Object[]{menu.syncId, containerId, menuSlot, menu.slots.size()}
               );
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean cursorEmpty() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return player != null && player.currentScreenHandler.getCursorStack().isEmpty();
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
      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayerEntity player = client.player;
      if (player != null && client.interactionManager != null) {
         ScreenHandler menu = player.currentScreenHandler;
         if (menu.syncId == containerId && menu.getRevision() == stateId && cursorEmpty()) {
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
                  new Object[]{menu.syncId, containerId, menu.getRevision(), stateId, cursorEmpty()}
               );
            return false;
         }
      } else {
         return false;
      }
   }

   public record PlannedClick(int menuSlot, int button, SlotActionType input) {
      public PlannedClick(int menuSlot, int button, SlotActionType input) {
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
