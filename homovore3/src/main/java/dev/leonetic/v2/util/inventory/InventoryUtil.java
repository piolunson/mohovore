package dev.leonetic.v2.util.inventory;

import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.util.network.NetworkAction;
import dev.leonetic.v2.util.network.NetworkActionContext;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import dev.leonetic.v2.util.network.PacketLimiter;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

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

   public static void capture(MinecraftClient client) {
      Objects.requireNonNull(client, "client");
      if (!client.isOnThread()) {
         throw new IllegalStateException("Inventory snapshots must be captured on the client thread");
      }

      ClientPlayerEntity player = client.player;
      if (player == null) {
         clear();
      } else {
         PlayerInventory inventory = player.getInventory();
         ScreenHandler menu = player.currentScreenHandler;
         int size = inventory.size();
         List<ItemStack> items = new ArrayList<>(size);

         for (int slot = 0; slot < size; slot++) {
            items.add(inventory.getStack(slot).copy());
         }

         int[] menuSlots = new int[size];
         Arrays.fill(menuSlots, -1);

         for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = (Slot)menu.slots.get(menuSlot);
            int inventorySlot = slot.getIndex();
            if (slot.inventory == inventory && inventorySlot >= 0 && inventorySlot < size) {
               menuSlots[inventorySlot] = menuSlot;
            }
         }

         InventorySnapshot snapshot = new InventorySnapshot(
            VERSIONS.incrementAndGet(),
            inventory.getSelectedSlot(),
            player.getId(),
            menu.syncId,
            menu.getRevision(),
            player.isUsingItem(),
            player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND,
            player.isDead(),
            player.isSprinting(),
            player.isSneaking(),
            player.getAbilities().flying,
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getEyeY(),
            possibleEyeHeights(player.getPose(), player.getScale()),
            estimatedAttackDamage(player),
            player.getYaw(),
            player.getPitch(),
            player.isOnGround(),
            player.horizontalCollision,
            List.copyOf(items),
            menu.getCursorStack(),
            player.getOffHandStack(),
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

   public static ItemStack cursor() {
      return snapshot().cursor();
   }

   public static boolean cursorEmpty() {
      return snapshot().cursorEmpty();
   }

   public static int selected() {
      return snapshot().selectedSlot();
   }

   public static Result find(Item target, Set<ResultType> scopes) {
      Objects.requireNonNull(target, "target");
      return find(stack -> stack.isOf(target), scopes);
   }

   public static Result find(Predicate<ItemStack> predicate, Set<ResultType> scopes) {
      Objects.requireNonNull(predicate, "predicate");
      Objects.requireNonNull(scopes, "scopes");
      return find((stack, type) -> scopes.contains(type) && predicate.test(stack));
   }

   public static Result find(BiPredicate<ItemStack, ResultType> predicate) {
      Objects.requireNonNull(predicate, "predicate");
      InventorySnapshot snapshot = SNAPSHOT.get();
      if (!snapshot.available()) {
         return none(snapshot);
      }

      ItemStack offhand = snapshot.offhandInternal();
      if (predicate.test(offhand.copy(), ResultType.OFFHAND)) {
         return result(snapshot, -1, offhand, ResultType.OFFHAND, true);
      }

      int searchable = Math.min(36, snapshot.size());

      for (int slot = 0; slot < searchable; slot++) {
         ItemStack stack = snapshot.itemInternal(slot);
         ResultType type = slot < PlayerInventory.getHotbarSize() ? ResultType.HOTBAR : ResultType.INVENTORY;
         if (predicate.test(stack.copy(), type)) {
            return result(snapshot, slot, stack, type, slot == snapshot.selectedSlot());
         }
      }

      return none(snapshot);
   }

   public static Result resultAt(InventorySnapshot snapshot, int slot) {
      Objects.requireNonNull(snapshot, "snapshot");
      if (snapshot.available() && slot >= 0 && slot < Math.min(36, snapshot.size())) {
         ItemStack stack = snapshot.itemInternal(slot);
         if (stack.isEmpty()) {
            return none(snapshot);
         }

         ResultType type = slot < PlayerInventory.getHotbarSize() ? ResultType.HOTBAR : ResultType.INVENTORY;
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
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.isOnThread()) {
            ClientPlayerEntity player = client.player;
            InventorySnapshot snapshot = SNAPSHOT.get();
            if (player != null
               && client.interactionManager != null
               && snapshot.available()
               && snapshot.menuId() == 0
               && player.currentScreenHandler.syncId == 0
               && snapshot.cursorEmpty()
               && player.currentScreenHandler.getCursorStack().isEmpty()
               && stillMatches(result, snapshot)) {
               int menuSlot = snapshot.menuSlot(result.slot());
               return menuSlot < 0
                  ? CompletableFuture.completedFuture(false)
                  : CompletableFuture.completedFuture(clickLocally(snapshot.menuId(), menuSlot, 40, SlotActionType.SWAP, -1));
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
      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayerEntity player = client.player;
      InventorySnapshot snapshot = SNAPSHOT.get();
      if (player != null
         && client.interactionManager != null
         && snapshot.available()
         && snapshot.menuId() == 0
         && player.currentScreenHandler.syncId == 0
         && player.currentScreenHandler.getCursorStack().isEmpty()
         && stillMatches(result, snapshot)) {
         int menuSlot = snapshot.menuSlot(result.slot());
         if (menuSlot < 0) {
            return false;
         }

         if (!player.getOffHandStack().isEmpty()) {
            player.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            capture(client);
         }

         return clickLocally(0, menuSlot, 40, SlotActionType.SWAP, -1);
      } else {
         return false;
      }
   }

   public static boolean swapWithEquipment(int inventorySlot, EquipmentSlot equipment) {
      Objects.requireNonNull(equipment, "equipment");
      MinecraftClient client = MinecraftClient.getInstance();
      if (!client.isOnThread()) {
         throw new IllegalStateException("Equipment swaps must be applied on the client thread");
      }

      ClientPlayerEntity player = client.player;
      InventorySnapshot snapshot = SNAPSHOT.get();
      if (player != null
         && client.interactionManager != null
         && snapshot.available()
         && snapshot.menuId() == 0
         && player.currentScreenHandler.syncId == 0
         && snapshot.cursorEmpty()
         && player.currentScreenHandler.getCursorStack().isEmpty()
         && inventorySlot >= 0
         && inventorySlot < 36) {
         int equipmentSlot = -1;
         ObjectIterator source = PlayerInventory.EQUIPMENT_SLOTS.int2ObjectEntrySet().iterator();

         while (source.hasNext()) {
            Entry<EquipmentSlot> entry = (Entry<EquipmentSlot>)source.next();
            if (entry.getValue() == equipment) {
               equipmentSlot = entry.getIntKey();
            }
         }

         if (equipmentSlot < 0) {
            return false;
         }

         ItemStack sourcex = snapshot.itemInternal(inventorySlot);
         ItemStack worn = snapshot.itemInternal(equipmentSlot);
         if (sourcex.isEmpty() && worn.isEmpty()) {
            return false;
         }

         if (!sourcex.isEmpty()) {
            EquippableComponent equippable = (EquippableComponent)sourcex.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null || equippable.slot() != equipment) {
               return false;
            }
         }

         int from = snapshot.menuSlot(inventorySlot);
         int to = snapshot.menuSlot(equipmentSlot);
         if (from >= 0 && to >= 0) {
            Homovore2.LOGGER.info("[Inventory] equipment swap inventorySlot={} <-> {}", inventorySlot, equipment);
            if (inventorySlot < PlayerInventory.getHotbarSize()) {
               return clickLocally(0, to, inventorySlot, SlotActionType.SWAP, 6);
            }

            if (!clicksFit(3, 6)) {
               return false;
            }

            long observedBefore = NetworkThreadExecutor.containerClicksObserved();
            int applied = 0;

            try {
               int first = sourcex.isEmpty() ? to : from;
               int second = sourcex.isEmpty() ? from : to;
               if (!ContainerClicks.apply(0, first, 0, SlotActionType.PICKUP)) {
                  return false;
               }

               applied++;
               if (ContainerClicks.apply(0, second, 0, SlotActionType.PICKUP)) {
                  applied++;
               }

               if (!ContainerClicks.cursorEmpty() && ContainerClicks.apply(0, first, 0, SlotActionType.PICKUP)) {
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

   public static CompletableFuture<Boolean> refillHotbar(InventorySnapshot snapshot, int hotbarSlot, int sourceSlot, ItemStack template, boolean quickMove) {
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(template, "template");
      if (snapshot.available()
         && hotbarSlot >= 0
         && hotbarSlot < PlayerInventory.getHotbarSize()
         && sourceSlot >= PlayerInventory.getHotbarSize()
         && sourceSlot < 36) {
         ItemStack targetStack = snapshot.itemInternal(hotbarSlot);
         ItemStack sourceStack = snapshot.itemInternal(sourceSlot);
         if (!template.isEmpty()
            && !sourceStack.isEmpty()
            && (targetStack.isEmpty() || ItemStack.areItemsAndComponentsEqual(targetStack, template))
            && ItemStack.areItemsAndComponentsEqual(sourceStack, template)) {
            Result target = result(snapshot, hotbarSlot, template, ResultType.HOTBAR, hotbarSlot == snapshot.selectedSlot());
            Result source = result(snapshot, sourceSlot, sourceStack, ResultType.INVENTORY, false);
            boolean targetedSwap = quickMove && !targetStack.isEmpty();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.isOnThread()) {
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

   private static boolean refillHotbarLocally(MinecraftClient client, InventorySnapshot snapshot, Result target, Result source, boolean targetedSwap) {
      ClientPlayerEntity player = client.player;
      if (player != null && client.interactionManager != null) {
         ScreenHandler menu = player.currentScreenHandler;
         if (snapshot.menuId() == menu.syncId
            && target.menuId() == menu.syncId
            && source.menuId() == menu.syncId
            && snapshot.cursorEmpty()
            && menu.getCursorStack().isEmpty()
            && stillMatches(target, snapshot)
            && stillMatches(source, snapshot)) {
            int from = snapshot.menuSlot(source.slot());
            int to = snapshot.menuSlot(target.slot());
            if (from >= 0 && to >= 0 && from != to) {
               ItemStack targetStack = snapshot.itemInternal(target.slot());
               ItemStack sourceStack = snapshot.itemInternal(source.slot());
               int containerId = menu.syncId;
               if (!targetStack.isEmpty() && (!targetedSwap || sourceStack.getCount() <= targetStack.getCount())) {
                  if (ItemStack.areItemsAndComponentsEqual(sourceStack, targetStack) && targetStack.getCount() < targetStack.getMaxCount()) {
                     Homovore2.LOGGER.info("[Inventory] refill merge inventorySlot={} -> hotbarSlot={}", source.slot(), target.slot());
                     if (!clicksFit(3, 6)) {
                        return false;
                     }

                     long observedBefore = NetworkThreadExecutor.containerClicksObserved();
                     int applied = 0;

                     try {
                        if (!ContainerClicks.apply(containerId, from, 0, SlotActionType.PICKUP)) {
                           return false;
                        }

                        applied++;
                        if (ContainerClicks.apply(containerId, to, 0, SlotActionType.PICKUP)) {
                           applied++;
                        }

                        if (!ContainerClicks.cursorEmpty() && ContainerClicks.apply(containerId, from, 0, SlotActionType.PICKUP)) {
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
                  return clickLocally(containerId, from, target.slot(), SlotActionType.SWAP, 6);
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

   private static boolean clickLocally(int containerId, int menuSlot, int button, SlotActionType input, int reserve) {
      if (!clicksFit(1, reserve)) {
         return false;
      }

      long observedBefore = NetworkThreadExecutor.containerClicksObserved();
      boolean applied = ContainerClicks.apply(containerId, menuSlot, button, input);
      NetworkThreadExecutor.reconcileDispatchedClicks(observedBefore, applied ? 1 : 0);
      return applied;
   }

   public static CompletableFuture<Boolean> click(int menuSlot, int button, SlotActionType input) {
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
            ? ItemStack.areEqual(result.stack(), snapshot.offhandInternal())
            : result.slot() >= 0 && result.slot() < snapshot.size() && ItemStack.areEqual(result.stack(), snapshot.itemInternal(result.slot()));
      }
   }

   private static Result result(InventorySnapshot snapshot, int slot, ItemStack stack, ResultType type, boolean holding) {
      return new Result(slot, stack, type, holding, snapshot.version(), snapshot.menuId(), slot < 0 ? -1 : snapshot.menuSlot(slot));
   }

   private static Result none(InventorySnapshot snapshot) {
      return new Result(-1, ItemStack.EMPTY, ResultType.NONE, false, snapshot.version(), snapshot.menuId(), -1);
   }

   private static double[] possibleEyeHeights(EntityPose pose, float scale) {
      double standing = 1.62 * scale;
      double crouching = 1.27 * scale;
      double swimming = 0.4 * scale;

      return switch (pose) {
         case GLIDING, SPIN_ATTACK, SWIMMING -> new double[]{swimming, standing, crouching};
         case CROUCHING -> new double[]{crouching, standing, swimming};
         default -> new double[]{standing, crouching, swimming};
      };
   }

   private static float estimatedAttackDamage(ClientPlayerEntity player) {
      float damage = 1.0F;
      StatusEffectInstance strength = player.getStatusEffect(StatusEffects.STRENGTH);
      if (strength != null) {
         damage += 3.0F * (strength.getAmplifier() + 1);
      }

      StatusEffectInstance weakness = player.getStatusEffect(StatusEffects.WEAKNESS);
      if (weakness != null) {
         damage -= 4.0F * (weakness.getAmplifier() + 1);
      }

      return damage;
   }
}
