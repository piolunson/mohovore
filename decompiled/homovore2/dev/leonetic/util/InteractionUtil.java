package dev.leonetic.util;

import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkThreadExecutor;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.class_1268;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_3965;

public final class InteractionUtil {
   private InteractionUtil() {
      throw new AssertionError();
   }

   public static CompletableFuture<Void> swing(class_1268 hand) {
      Objects.requireNonNull(hand, "hand");
      return NetworkThreadExecutor.execute(context -> context.swing(hand));
   }

   public static CompletableFuture<Void> useItem(class_1268 hand) {
      Objects.requireNonNull(hand, "hand");
      return NetworkThreadExecutor.execute(context -> context.useItem(hand));
   }

   public static CompletableFuture<Void> useItem(class_1268 hand, float yaw, float pitch) {
      Objects.requireNonNull(hand, "hand");
      return NetworkThreadExecutor.execute(context -> context.useItem(hand, yaw, pitch));
   }

   public static CompletableFuture<Boolean> useItem(Result item) {
      Objects.requireNonNull(item, "item");
      return InventoryUtil.withSwap(item, context -> context.useItem(item.hand()));
   }

   public static CompletableFuture<Void> useItemOn(class_1268 hand, class_3965 hit, boolean swing) {
      Objects.requireNonNull(hand, "hand");
      class_3965 immutableHit = immutableHit(hit);
      return NetworkThreadExecutor.execute(context -> context.place(hand, immutableHit, swing));
   }

   public static CompletableFuture<Boolean> useItemOn(Result item, class_3965 hit, boolean swing) {
      Objects.requireNonNull(item, "item");
      class_3965 immutableHit = immutableHit(hit);
      return InventoryUtil.withSwap(item, context -> context.place(item.hand(), immutableHit, swing));
   }

   public static CompletableFuture<Void> place(class_1268 hand, class_3965 hit, boolean swing) {
      return useItemOn(hand, hit, swing);
   }

   public static CompletableFuture<Boolean> place(Result item, class_3965 hit, boolean swing) {
      return useItemOn(item, hit, swing);
   }

   public static CompletableFuture<Void> breakBlock(class_2338 pos, class_2350 direction, boolean swing) {
      class_2338 immutablePos = Objects.requireNonNull(pos, "pos").method_10062();
      Objects.requireNonNull(direction, "direction");
      return NetworkThreadExecutor.execute(context -> context.breakBlock(immutablePos, direction, swing));
   }

   public static CompletableFuture<Boolean> breakBlock(Result tool, class_2338 pos, class_2350 direction, boolean swing) {
      Objects.requireNonNull(tool, "tool");
      class_2338 immutablePos = Objects.requireNonNull(pos, "pos").method_10062();
      Objects.requireNonNull(direction, "direction");
      return InventoryUtil.withSwap(tool, context -> context.breakBlock(immutablePos, direction, swing));
   }

   public static CompletableFuture<Void> attack(int entityId) {
      return NetworkThreadExecutor.execute(context -> context.attack(entityId));
   }

   public static CompletableFuture<Boolean> attack(Result weapon, int entityId) {
      Objects.requireNonNull(weapon, "weapon");
      return InventoryUtil.withSwap(weapon, context -> context.attack(entityId));
   }

   public static CompletableFuture<Void> interact(int entityId, class_1268 hand, class_243 location, boolean secondaryAction) {
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(location, "location");
      return NetworkThreadExecutor.execute(context -> context.interact(entityId, hand, location, secondaryAction));
   }

   public static class_2350 right(class_2350 direction) {
      return switch ((class_2350)Objects.requireNonNull(direction, "direction")) {
         case field_11034 -> class_2350.field_11035;
         case field_11035 -> class_2350.field_11039;
         case field_11039 -> class_2350.field_11043;
         case field_11043 -> class_2350.field_11034;
         default -> throw new IllegalArgumentException("Direction must be horizontal: " + direction);
      };
   }

   public static class_2350 left(class_2350 direction) {
      return switch ((class_2350)Objects.requireNonNull(direction, "direction")) {
         case field_11034 -> class_2350.field_11043;
         case field_11035 -> class_2350.field_11034;
         case field_11039 -> class_2350.field_11035;
         case field_11043 -> class_2350.field_11039;
         default -> throw new IllegalArgumentException("Direction must be horizontal: " + direction);
      };
   }

   private static class_3965 immutableHit(class_3965 hit) {
      Objects.requireNonNull(hit, "hit");
      class_3965 copy = new class_3965(hit.method_17784(), hit.method_17780(), hit.method_17777().method_10062(), hit.method_17781());
      return hit.method_62877() ? copy.method_62876() : copy;
   }
}
