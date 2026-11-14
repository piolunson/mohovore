package dev.leonetic.util.inventory;

import dev.leonetic.Homovore2;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.NetworkActionContext;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.class_10192;
import net.minecraft.class_1268;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1304;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_4050;
import net.minecraft.class_746;
import net.minecraft.class_9334;

public final class InventoryUtil {
   public static final Set<ResultType> HOTBAR_SCOPE = Set.of(ResultType.OFFHAND, ResultType.HOTBAR);
   public static final Set<ResultType> INVENTORY_SCOPE = Set.of(ResultType.OFFHAND, ResultType.INVENTORY);
   public static final Set<ResultType> MAIN_INVENTORY_SCOPE = Set.of(ResultType.HOTBAR, ResultType.INVENTORY);
   public static final Set<ResultType> FULL_SCOPE = Set.of(ResultType.OFFHAND, ResultType.HOTBAR, ResultType.INVENTORY);
   private static final AtomicLong VERSIONS = new AtomicLong();
   private static final AtomicReference<InventorySnapshot> SNAPSHOT = new AtomicReference<>(InventorySnapshot.EMPTY);
   private static final int OPTIONAL_CLICK_RESERVE = 6;
   private static final int UNBUDGETED = -1;

   private InventoryUtil() {
      throw new AssertionError();
   }

   public static void capture(class_310 client) {
      Objects.requireNonNull(client, "client");
      if (!client.method_18854()) {
         throw new IllegalStateException("Inventory snapshots must be captured on the client thread");
      }

      class_746 player = client.field_1724;
      if (player == null) {
         clear();
      } else {
         class_1661 inventory = player.method_31548();
         class_1703 menu = player.field_7512;
         int size = inventory.method_5439();
         List<class_1799> items = new ArrayList<>(size);

         for (int slot = 0; slot < size; slot++) {
            items.add(inventory.method_5438(slot).method_7972());
         }

         int[] menuSlots = new int[size];
         Arrays.fill(menuSlots, -1);

         for (int menuSlot = 0; menuSlot < menu.field_7761.size(); menuSlot++) {
            class_1735 slot = (class_1735)menu.field_7761.get(menuSlot);
            int inventorySlot = slot.method_34266();
            if (slot.field_7871 == inventory && inventorySlot >= 0 && inventorySlot < size) {
               menuSlots[inventorySlot] = menuSlot;
            }
         }

         InventorySnapshot snapshot = new InventorySnapshot(
            VERSIONS.incrementAndGet(),
            inventory.method_67532(),
            player.method_5628(),
            menu.field_7763,
            menu.method_37421(),
            player.method_6115(),
            player.method_6115() && player.method_6058() == class_1268.field_5808,
            player.method_29504(),
            player.method_5624(),
            player.method_5715(),
            player.method_31549().field_7479,
            player.method_23317(),
            player.method_23318(),
            player.method_23321(),
            player.method_23320(),
            possibleEyeHeights(player.method_18376(), player.method_55693()),
            estimatedAttackDamage(player),
            player.method_36454(),
            player.method_36455(),
            player.method_24828(),
            player.field_5976,
            List.copyOf(items),
            menu.method_34255(),
            player.method_6079(),
            menuSlots
         );
         SNAPSHOT.set(snapshot);
         NetworkThreadExecutor.initializeSelectedSlot(snapshot.selectedSlot());
      }
   }

   public static void clear() {
      SNAPSHOT.set(InventorySnapshot.EMPTY);
   }

   public static InventorySnapshot snapshot() {
      return SNAPSHOT.get();
   }

   public static class_1799 cursor() {
      return snapshot().cursor();
   }

   public static boolean cursorEmpty() {
      return snapshot().cursorEmpty();
   }

   public static int selected() {
      return snapshot().selectedSlot();
   }

   public static Result find(class_1792 target, Set<ResultType> scopes) {
      Objects.requireNonNull(target, "target");
      return find(stack -> stack.method_31574(target), scopes);
   }

   public static Result find(Predicate<class_1799> predicate, Set<ResultType> scopes) {
      Objects.requireNonNull(predicate, "predicate");
      Objects.requireNonNull(scopes, "scopes");
      return find((stack, type) -> scopes.contains(type) && predicate.test(stack));
   }

   public static Result find(BiPredicate<class_1799, ResultType> predicate) {
      Objects.requireNonNull(predicate, "predicate");
      InventorySnapshot snapshot = SNAPSHOT.get();
      if (!snapshot.available()) {
         return none(snapshot);
      }

      class_1799 offhand = snapshot.offhandInternal();
      if (predicate.test(offhand.method_7972(), ResultType.OFFHAND)) {
         return result(snapshot, -1, offhand, ResultType.OFFHAND, true);
      }

      int searchable = Math.min(36, snapshot.size());

      for (int slot = 0; slot < searchable; slot++) {
         class_1799 stack = snapshot.itemInternal(slot);
         ResultType type = slot < class_1661.method_7368() ? ResultType.HOTBAR : ResultType.INVENTORY;
         if (predicate.test(stack.method_7972(), type)) {
            return result(snapshot, slot, stack, type, slot == snapshot.selectedSlot());
         }
      }

      return none(snapshot);
   }

   public static Result resultAt(InventorySnapshot snapshot, int slot) {
      Objects.requireNonNull(snapshot, "snapshot");
      if (snapshot.available() && slot >= 0 && slot < Math.min(36, snapshot.size())) {
         class_1799 stack = snapshot.itemInternal(slot);
         if (stack.method_7960()) {
            return none(snapshot);
         }

         ResultType type = slot < class_1661.method_7368() ? ResultType.HOTBAR : ResultType.INVENTORY;
         return result(snapshot, slot, stack, type, slot == snapshot.selectedSlot());
      } else {
         return none(snapshot);
      }
   }

   public static CompletableFuture<Boolean> withSwap(Result result, NetworkAction action) {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      if (!result.found()) {
         return CompletableFuture.completedFuture(false);
      }

      try {
         return NetworkThreadExecutor.swaps().withSwap(result, action);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }
   }

   public static CompletableFuture<Boolean> withSwapSequence(List<SwapManager.Step> steps) {
      Objects.requireNonNull(steps, "steps");

      try {
         return NetworkThreadExecutor.swaps().sequence(steps);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }
   }

   public static boolean withSwapNow(NetworkActionContext context, Result result, NetworkAction action) {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      return context.swaps().withSwapNow(context, result, action);
   }

   public static CompletableFuture<Boolean> withHeld(Result result, NetworkAction action) {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");

      try {
         return NetworkThreadExecutor.swaps().withHeld(result, action);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }
   }

   public static CompletableFuture<SwapManager.MainhandLease> leaseMainhand(Result result) {
      Objects.requireNonNull(result, "result");
      if (!result.found()) {
         return CompletableFuture.completedFuture(null);
      }

      try {
         return NetworkThreadExecutor.swaps().leaseMainhand(result);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }
   }

   public static CompletableFuture<Boolean> withSwap(Result result, Runnable action) {
      Objects.requireNonNull(action, "action");
      return withSwap(result, context -> action.run());
   }

   public static CompletableFuture<Boolean> swap(int hotbarSlot) {
      try {
         return NetworkThreadExecutor.slots().select(hotbarSlot);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }
   }

   public static CompletableFuture<Boolean> swapToOffhand(Result result) {
      Objects.requireNonNull(result, "result");
      if (result.found() && result.type() != ResultType.OFFHAND) {
         class_310 client = class_310.method_1551();
         if (client.method_18854()) {
            class_746 player = client.field_1724;
            InventorySnapshot snapshot = SNAPSHOT.get();
            if (player != null
               && client.field_1761 != null
               && snapshot.available()
               && snapshot.menuId() == 0
               && player.field_7512.field_7763 == 0
               && snapshot.cursorEmpty()
               && player.field_7512.method_34255().method_7960()
               && stillMatches(result, snapshot)) {
               int menuSlot = snapshot.menuSlot(result.slot());
               return menuSlot < 0
                  ? CompletableFuture.completedFuture(false)
                  : CompletableFuture.completedFuture(clickLocally(snapshot.menuId(), menuSlot, 40, class_1713.field_7791, -1));
            } else {
               return CompletableFuture.completedFuture(false);
            }
         } else {
            try {
               return NetworkThreadExecutor.swaps().moveToOffhand(result);
            } catch (RuntimeException exception) {
               return CompletableFuture.failedFuture(exception);
            }
         }
      } else {
         return CompletableFuture.completedFuture(false);
      }
   }

   public static CompletableFuture<Boolean> refillConsumedOffhand(Result result) {
      Objects.requireNonNull(result, "result");
      if (result.found() && result.type() != ResultType.OFFHAND) {
         CompletableFuture<Boolean> applied = new CompletableFuture<>();
         ContainerClicks.dispatch(() -> {
            try {
               applied.complete(refillConsumedOffhandNow(result));
            } catch (Throwable throwable) {
               applied.completeExceptionally(throwable);
            }
         });
         return applied;
      } else {
         return CompletableFuture.completedFuture(false);
      }
   }

   private static boolean refillConsumedOffhandNow(Result result) {
      class_310 client = class_310.method_1551();
      class_746 player = client.field_1724;
      InventorySnapshot snapshot = SNAPSHOT.get();
      if (player != null
         && client.field_1761 != null
         && snapshot.available()
         && snapshot.menuId() == 0
         && player.field_7512.field_7763 == 0
         && player.field_7512.method_34255().method_7960()
         && stillMatches(result, snapshot)) {
         int menuSlot = snapshot.menuSlot(result.slot());
         if (menuSlot < 0) {
            return false;
         }

         if (!player.method_6079().method_7960()) {
            player.method_5673(class_1304.field_6171, class_1799.field_8037);
            capture(client);
         }

         return clickLocally(0, menuSlot, 40, class_1713.field_7791, -1);
      } else {
         return false;
      }
   }

   public static boolean swapWithEquipment(int inventorySlot, class_1304 equipment) {
      Objects.requireNonNull(equipment, "equipment");
      class_310 client = class_310.method_1551();
      if (!client.method_18854()) {
         throw new IllegalStateException("Equipment swaps must be applied on the client thread");
      }

      class_746 player = client.field_1724;
      InventorySnapshot snapshot = SNAPSHOT.get();
      if (player != null
         && client.field_1761 != null
         && snapshot.available()
         && snapshot.menuId() == 0
         && player.field_7512.field_7763 == 0
         && snapshot.cursorEmpty()
         && player.field_7512.method_34255().method_7960()
         && inventorySlot >= 0
         && inventorySlot < 36) {
         int equipmentSlot = -1;
         ObjectIterator source = class_1661.field_56551.int2ObjectEntrySet().iterator();

         while (source.hasNext()) {
            Entry<class_1304> entry = (Entry<class_1304>)source.next();
            if (entry.getValue() == equipment) {
               equipmentSlot = entry.getIntKey();
            }
         }

         if (equipmentSlot < 0) {
            return false;
         }

         class_1799 sourcex = snapshot.itemInternal(inventorySlot);
         class_1799 worn = snapshot.itemInternal(equipmentSlot);
         if (sourcex.method_7960() && worn.method_7960()) {
            return false;
         }

         if (!sourcex.method_7960()) {
            class_10192 equippable = (class_10192)sourcex.method_58694(class_9334.field_54196);
            if (equippable == null || equippable.comp_3174() != equipment) {
               return false;
            }
         }

         int from = snapshot.menuSlot(inventorySlot);
         int to = snapshot.menuSlot(equipmentSlot);
         if (from >= 0 && to >= 0) {
            Homovore2.LOGGER.info("[Inventory] equipment swap inventorySlot={} <-> {}", inventorySlot, equipment);
            if (inventorySlot < class_1661.method_7368()) {
               return clickLocally(0, to, inventorySlot, class_1713.field_7791, 6);
            }

            if (!clicksFit(3, 6)) {
               return false;
            }

            long observedBefore = NetworkThreadExecutor.containerClicksObserved();
            int applied = 0;

            try {
               int first = sourcex.method_7960() ? to : from;
               int second = sourcex.method_7960() ? from : to;
               if (!ContainerClicks.apply(0, first, 0, class_1713.field_7790)) {
                  return false;
               }

               applied++;
               if (ContainerClicks.apply(0, second, 0, class_1713.field_7790)) {
                  applied++;
               }

               if (!ContainerClicks.cursorEmpty() && ContainerClicks.apply(0, first, 0, class_1713.field_7790)) {
                  applied++;
               }

               if (!ContainerClicks.cursorEmpty()) {
                  Homovore2.LOGGER.warn("[Inventory] equipment swap left an item on the cursor");
               }

               return true;
            } finally {
               NetworkThreadExecutor.reconcileDispatchedClicks(observedBefore, applied);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static CompletableFuture<Boolean> refillHotbar(InventorySnapshot snapshot, int hotbarSlot, int sourceSlot, class_1799 template, boolean quickMove) {
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(template, "template");
      if (snapshot.available() && hotbarSlot >= 0 && hotbarSlot < class_1661.method_7368() && sourceSlot >= class_1661.method_7368() && sourceSlot < 36) {
         class_1799 targetStack = snapshot.itemInternal(hotbarSlot);
         class_1799 sourceStack = snapshot.itemInternal(sourceSlot);
         if (!template.method_7960()
            && !sourceStack.method_7960()
            && (targetStack.method_7960() || class_1799.method_31577(targetStack, template))
            && class_1799.method_31577(sourceStack, template)) {
            Result target = result(snapshot, hotbarSlot, template, ResultType.HOTBAR, hotbarSlot == snapshot.selectedSlot());
            Result source = result(snapshot, sourceSlot, sourceStack, ResultType.INVENTORY, false);
            boolean targetedSwap = quickMove && !targetStack.method_7960();
            class_310 client = class_310.method_1551();
            if (client.method_18854()) {
               return CompletableFuture.completedFuture(refillHotbarLocally(client, snapshot, target, source, targetedSwap));
            }

            try {
               return NetworkThreadExecutor.swaps().refillHotbar(target, source, targetedSwap);
            } catch (RuntimeException exception) {
               return CompletableFuture.failedFuture(exception);
            }
         } else {
            return CompletableFuture.completedFuture(false);
         }
      } else {
         return CompletableFuture.completedFuture(false);
      }
   }

   private static boolean refillHotbarLocally(class_310 client, InventorySnapshot snapshot, Result target, Result source, boolean targetedSwap) {
      class_746 player = client.field_1724;
      if (player != null && client.field_1761 != null) {
         class_1703 menu = player.field_7512;
         if (snapshot.menuId() == menu.field_7763
            && target.menuId() == menu.field_7763
            && source.menuId() == menu.field_7763
            && snapshot.cursorEmpty()
            && menu.method_34255().method_7960()
            && stillMatches(target, snapshot)
            && stillMatches(source, snapshot)) {
            int from = snapshot.menuSlot(source.slot());
            int to = snapshot.menuSlot(target.slot());
            if (from >= 0 && to >= 0 && from != to) {
               class_1799 targetStack = snapshot.itemInternal(target.slot());
               class_1799 sourceStack = snapshot.itemInternal(source.slot());
               int containerId = menu.field_7763;
               if (!targetStack.method_7960() && (!targetedSwap || sourceStack.method_7947() <= targetStack.method_7947())) {
                  if (class_1799.method_31577(sourceStack, targetStack) && targetStack.method_7947() < targetStack.method_7914()) {
                     Homovore2.LOGGER.info("[Inventory] refill merge inventorySlot={} -> hotbarSlot={}", source.slot(), target.slot());
                     if (!clicksFit(3, 6)) {
                        return false;
                     }

                     long observedBefore = NetworkThreadExecutor.containerClicksObserved();
                     int applied = 0;

                     try {
                        if (!ContainerClicks.apply(containerId, from, 0, class_1713.field_7790)) {
                           return false;
                        }

                        applied++;
                        if (ContainerClicks.apply(containerId, to, 0, class_1713.field_7790)) {
                           applied++;
                        }

                        if (!ContainerClicks.cursorEmpty() && ContainerClicks.apply(containerId, from, 0, class_1713.field_7790)) {
                           applied++;
                        }

                        if (!ContainerClicks.cursorEmpty()) {
                           Homovore2.LOGGER.warn("[Inventory] refill merge left an item on the cursor");
                        }

                        return true;
                     } finally {
                        NetworkThreadExecutor.reconcileDispatchedClicks(observedBefore, applied);
                     }
                  } else {
                     return false;
                  }
               } else {
                  Homovore2.LOGGER.info("[Inventory] refill targeted-swap inventorySlot={} -> hotbarSlot={}", source.slot(), target.slot());
                  return clickLocally(containerId, from, target.slot(), class_1713.field_7791, 6);
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean clicksFit(int clicks, int reserve) {
      if (reserve == -1) {
         return true;
      }

      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      if (limiter == null) {
         return true;
      }

      if (limiter.clickSlotHeadroomOffThread(reserve) >= clicks) {
         return true;
      }

      Homovore2.LOGGER.info("[Inventory] client-thread click deferred by packet budget required={} reserve={}", clicks, reserve);
      return false;
   }

   private static boolean clickLocally(int containerId, int menuSlot, int button, class_1713 input, int reserve) {
      if (!clicksFit(1, reserve)) {
         return false;
      }

      long observedBefore = NetworkThreadExecutor.containerClicksObserved();
      boolean applied = ContainerClicks.apply(containerId, menuSlot, button, input);
      NetworkThreadExecutor.reconcileDispatchedClicks(observedBefore, applied ? 1 : 0);
      return applied;
   }

   public static CompletableFuture<Boolean> click(int menuSlot, int button, class_1713 input) {
      try {
         return NetworkThreadExecutor.slots().click(menuSlot, button, input);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }
   }

   static boolean stillMatches(Result result, InventorySnapshot snapshot) {
      if (!snapshot.available()) {
         return false;
      } else {
         return result.type() == ResultType.OFFHAND
            ? class_1799.method_7973(result.stack(), snapshot.offhandInternal())
            : result.slot() >= 0 && result.slot() < snapshot.size() && class_1799.method_7973(result.stack(), snapshot.itemInternal(result.slot()));
      }
   }

   private static Result result(InventorySnapshot snapshot, int slot, class_1799 stack, ResultType type, boolean holding) {
      return new Result(slot, stack, type, holding, snapshot.version(), snapshot.menuId(), slot < 0 ? -1 : snapshot.menuSlot(slot));
   }

   private static Result none(InventorySnapshot snapshot) {
      return new Result(-1, class_1799.field_8037, ResultType.NONE, false, snapshot.version(), snapshot.menuId(), -1);
   }

   private static double[] possibleEyeHeights(class_4050 pose, float scale) {
      double standing = 1.62 * scale;
      double crouching = 1.27 * scale;
      double swimming = 0.4 * scale;

      return switch (pose) {
         case field_18077, field_18080, field_18079 -> new double[]{swimming, standing, crouching};
         case field_18081 -> new double[]{crouching, standing, swimming};
         default -> new double[]{standing, crouching, swimming};
      };
   }

   private static float estimatedAttackDamage(class_746 player) {
      float damage = 1.0F;
      class_1293 strength = player.method_6112(class_1294.field_5910);
      if (strength != null) {
         damage += 3.0F * (strength.method_5578() + 1);
      }

      class_1293 weakness = player.method_6112(class_1294.field_5911);
      if (weakness != null) {
         damage -= 4.0F * (weakness.method_5578() + 1);
      }

      return damage;
   }
}
