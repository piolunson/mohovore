package dev.leonetic.v2.util;

import dev.leonetic.v2.util.inventory.InventoryUtil;
import dev.leonetic.v2.util.inventory.Result;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class InteractionUtil {
   private InteractionUtil() {
      throw new AssertionError();
   }

   public static CompletableFuture<Void> swing(Hand hand) {
      Objects.requireNonNull(hand, "hand");
      return NetworkThreadExecutor.execute(context -> context.swing(hand));
   }

   public static CompletableFuture<Void> useItem(Hand hand) {
      Objects.requireNonNull(hand, "hand");
      return NetworkThreadExecutor.execute(context -> context.useItem(hand));
   }

   public static CompletableFuture<Void> useItem(Hand hand, float yaw, float pitch) {
      Objects.requireNonNull(hand, "hand");
      return NetworkThreadExecutor.execute(context -> context.useItem(hand, yaw, pitch));
   }

   public static CompletableFuture<Boolean> useItem(Result item) {
      Objects.requireNonNull(item, "item");
      return InventoryUtil.withSwap(item, context -> context.useItem(item.hand()));
   }

   public static CompletableFuture<Void> useItemOn(Hand hand, BlockHitResult hit, boolean swing) {
      Objects.requireNonNull(hand, "hand");
      BlockHitResult immutableHit = immutableHit(hit);
      return NetworkThreadExecutor.execute(context -> context.place(hand, immutableHit, swing));
   }

   public static CompletableFuture<Boolean> useItemOn(Result item, BlockHitResult hit, boolean swing) {
      Objects.requireNonNull(item, "item");
      BlockHitResult immutableHit = immutableHit(hit);
      return InventoryUtil.withSwap(item, context -> context.place(item.hand(), immutableHit, swing));
   }

   public static CompletableFuture<Void> place(Hand hand, BlockHitResult hit, boolean swing) {
      return useItemOn(hand, hit, swing);
   }

   public static CompletableFuture<Boolean> place(Result item, BlockHitResult hit, boolean swing) {
      return useItemOn(item, hit, swing);
   }

   public static CompletableFuture<Void> breakBlock(BlockPos pos, Direction direction, boolean swing) {
      BlockPos immutablePos = Objects.requireNonNull(pos, "pos").toImmutable();
      Objects.requireNonNull(direction, "direction");
      return NetworkThreadExecutor.execute(context -> context.breakBlock(immutablePos, direction, swing));
   }

   public static CompletableFuture<Boolean> breakBlock(Result tool, BlockPos pos, Direction direction, boolean swing) {
      Objects.requireNonNull(tool, "tool");
      BlockPos immutablePos = Objects.requireNonNull(pos, "pos").toImmutable();
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

   public static CompletableFuture<Void> interact(int entityId, Hand hand, Vec3d location, boolean secondaryAction) {
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(location, "location");
      return NetworkThreadExecutor.execute(context -> context.interact(entityId, hand, location, secondaryAction));
   }

   public static Direction right(Direction direction) {
      return switch ((Direction)Objects.requireNonNull(direction, "direction")) {
         case EAST -> Direction.SOUTH;
         case SOUTH -> Direction.WEST;
         case WEST -> Direction.NORTH;
         case NORTH -> Direction.EAST;
         default -> throw new IllegalArgumentException("Direction must be horizontal: " + direction);
      };
   }

   public static Direction left(Direction direction) {
      return switch ((Direction)Objects.requireNonNull(direction, "direction")) {
         case EAST -> Direction.NORTH;
         case SOUTH -> Direction.EAST;
         case WEST -> Direction.SOUTH;
         case NORTH -> Direction.WEST;
         default -> throw new IllegalArgumentException("Direction must be horizontal: " + direction);
      };
   }

   private static BlockHitResult immutableHit(BlockHitResult hit) {
      Objects.requireNonNull(hit, "hit");
      BlockHitResult copy = new BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos().toImmutable(), hit.isInsideBlock());
      return hit.isAgainstWorldBorder() ? copy.againstWorldBorder() : copy;
   }
}
