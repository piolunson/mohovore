package dev.leonetic.manager;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.Feature;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.inventory.SwapMode;
import dev.leonetic.util.player.EatUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PlacementManager extends Feature {
   private static final long WINDOW_MS = 300L;
   private static final int MAX_PER_WINDOW = 9;
   private static final long PER_BLOCK_COOLDOWN_MS = 50L;
   private static final EnumSet<ResultType> SCOPE = EnumSet.of(ResultType.HOTBAR);
   private final Map<BlockPos, Item> queue = new LinkedHashMap<>();
   private final Deque<Long> window = new ArrayDeque<>();
   private final Map<BlockPos, Long> cooldowns = new ConcurrentHashMap<>();
   private final CopyOnWriteArrayList<PlacementManager.PlacementListener> listeners = new CopyOnWriteArrayList<>();

   public void addListener(PlacementManager.PlacementListener listener) {
      this.listeners.addIfAbsent(listener);
   }

   public void removeListener(PlacementManager.PlacementListener listener) {
      this.listeners.remove(listener);
   }

   public PlacementManager() {
      EVENT_BUS.register(this);
   }

   public boolean enqueue(BlockPos pos, Item item) {
      if (pos == null || item == null) {
         return false;
      } else {
         return this.onCooldown(pos) ? false : this.queue.putIfAbsent(pos.toImmutable(), item) == null;
      }
   }

   public void removeQueuedFor(Predicate<BlockPos> filter) {
      this.queue.keySet().removeIf(filter);
   }

   public void forceResetPlaceCooldown(BlockPos pos) {
      this.cooldowns.remove(pos);
   }

   public void notePlacement(BlockPos pos) {
      long now = System.currentTimeMillis();
      this.cooldowns.put(pos.toImmutable(), now);
      this.window.addLast(now);
   }

   private boolean onCooldown(BlockPos pos) {
      Long last = this.cooldowns.get(pos);
      return last != null && System.currentTimeMillis() - last < 50L;
   }

   private int budget() {
      long cutoff = System.currentTimeMillis() - 300L;

      while (!this.window.isEmpty() && this.window.peekFirst() < cutoff) {
         this.window.pollFirst();
      }

      return 9 - this.window.size();
   }

   @Subscribe
   private void onTick(TickEvent event) {
      this.flushQueue();
   }

   public void flushQueue() {
      if (!nullCheck() && !this.queue.isEmpty()) {
         if (!EatUtil.shouldDefer()) {
            long now = System.currentTimeMillis();
            this.cooldowns.values().removeIf(t -> now - t >= 50L);
            int budget = this.budget();
            if (budget > 0) {
               Item item = this.queue.values().iterator().next();
               List<BlockPos> batch = new ArrayList<>();
               List<BlockPos> stale = new ArrayList<>();

               for (Entry<BlockPos, Item> entry : this.queue.entrySet()) {
                  if (entry.getValue() == item) {
                     BlockPos pos = entry.getKey();
                     if (!mc.world.getBlockState(pos).isReplaceable()) {
                        stale.add(pos);
                     } else if (PlaceUtil.canPlace(pos) && !this.cooldowns.containsKey(pos)) {
                        batch.add(pos);
                        if (batch.size() >= budget) {
                           break;
                        }
                     }
                  }
               }

               stale.forEach(this.queue::remove);
               if (!batch.isEmpty()) {
                  if (this.place(batch, item, 400)) {
                     batch.forEach(this.queue::remove);
                  }
               }
            }
         }
      }
   }

   private boolean place(List<BlockPos> batch, Item item, int priority) {
      boolean swapped = Homovore.swapManager.withSwap(item, SCOPE, SwapMode.SILENT, priority, () -> batch.forEach(posx -> this.sendPlace(posx, null)));
      if (!swapped) {
         return false;
      }

      long stamp = System.currentTimeMillis();

      for (BlockPos pos : batch) {
         this.cooldowns.put(pos, stamp);
         this.window.addLast(stamp);
      }

      return true;
   }

   public List<BlockPos> placeBatch(List<BlockPos> positions, Item item) {
      if (!nullCheck() && !positions.isEmpty() && !EatUtil.shouldDefer()) {
         int budget = this.budget();
         if (budget <= 0) {
            return List.of();
         }

         List<BlockPos> batch = new ArrayList<>();

         for (BlockPos pos : positions) {
            if (batch.size() >= budget) {
               break;
            }

            if (!this.onCooldown(pos) && PlaceUtil.canPlace(pos)) {
               batch.add(pos.toImmutable());
            }
         }

         if (batch.isEmpty()) {
            return List.of();
         } else {
            return this.place(batch, item, 400) ? batch : List.of();
         }
      } else {
         return List.of();
      }
   }

   public boolean placeDirect(BlockPos pos, @Nullable Direction face, Item item) {
      if (nullCheck() || EatUtil.shouldDefer()) {
         return false;
      }

      if (this.budget() <= 0 || this.onCooldown(pos)) {
         return false;
      }

      if (!PlaceUtil.canPlace(pos)) {
         return false;
      }

      boolean swapped = Homovore.swapManager.withSwap(item, SCOPE, SwapMode.SILENT, 400, () -> this.sendPlace(pos, face));
      if (!swapped) {
         return false;
      }

      this.notePlacement(pos);
      return true;
   }

   private void sendPlace(BlockPos pos, @Nullable Direction face) {
      Direction side = face != null ? face : this.placeSide(pos);
      BlockPos clicked;
      Vec3d hitPos;
      Direction hitSide;
      if (side == null) {
         hitSide = this.airPlaceDirection(pos);
         clicked = pos;
         hitPos = faceCenter(pos, hitSide);
      } else {
         clicked = pos.offset(side);
         hitPos = faceCenter(pos, side);
         hitSide = side.getOpposite();
      }

      if (mc.player.getMainHandStack().getItem() instanceof BlockItem blockItem) {
         BlockState desired = blockItem.getBlock().getDefaultState();
         if (desired.contains(Properties.AXIS)) {
            hitSide = switch ((Axis)desired.get(Properties.AXIS)) {
               case X -> Direction.EAST;
               case Z -> Direction.SOUTH;
               case Y -> Direction.UP;
               default -> throw new MatchException(null, null);
            };
            hitPos = faceCenter(pos, hitSide);
         }
      }

      this.send(new BlockHitResult(hitPos, hitSide, clicked, false), Hand.MAIN_HAND);
   }

   public boolean placeCrystal(BlockPos base, boolean trustBase) {
      if (!nullCheck() && !EatUtil.shouldDefer()) {
         BlockHitResult hit = this.crystalHit(base, trustBase);
         return hit == null ? false : Homovore.swapManager.withSwap(Items.END_CRYSTAL, SCOPE, SwapMode.SILENT, 350, () -> this.send(hit, Hand.MAIN_HAND));
      } else {
         return false;
      }
   }

   @Nullable
   private BlockHitResult crystalHit(BlockPos base, boolean trustBase) {
      if (!trustBase) {
         BlockState baseState = mc.world.getBlockState(base);
         if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) {
            return null;
         }
      }

      BlockPos airPos = base.up();
      BlockState airState = mc.world.getBlockState(airPos);
      if (airState.isAir() || airState.isOf(Blocks.FIRE) && mc.world.getRegistryKey().equals(World.END)) {
         for (Entity e : mc.world.getOtherEntities(null, new Box(airPos))) {
            if (!(e instanceof ItemEntity) && (!(e instanceof EndCrystalEntity crystal) || crystal.age >= 5 && !crystal.getBlockPos().equals(airPos))) {
               return null;
            }
         }

         Vec3d eye = mc.player.getCameraPosVec(1.0F);
         Vec3d baseCenter = Vec3d.ofCenter(base);
         double dx = eye.x - baseCenter.x;
         double dy = eye.y - baseCenter.y;
         double dz = eye.z - baseCenter.z;
         double absX = Math.abs(dx);
         double absY = Math.abs(dy);
         double absZ = Math.abs(dz);
         Direction clickFace;
         if (absY >= absX && absY >= absZ) {
            clickFace = dy > 0.0 ? Direction.UP : Direction.DOWN;
         } else if (absX >= absZ) {
            clickFace = dx > 0.0 ? Direction.EAST : Direction.WEST;
         } else {
            clickFace = dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
         }

         Vec3d playerPos = mc.player.getEntityPos();
         double offX = Math.clamp(playerPos.x - Math.floor(playerPos.x), 0.2, 0.8) - 0.5;
         double offY = Math.clamp(playerPos.y - Math.floor(playerPos.y), 0.2, 0.8) - 0.5;
         double offZ = Math.clamp(playerPos.z - Math.floor(playerPos.z), 0.2, 0.8) - 0.5;

         Vec3d hitVec = switch (clickFace) {
            case UP, DOWN -> baseCenter.add(offX, clickFace == Direction.UP ? 0.5 : -0.5, offZ);
            case NORTH, SOUTH -> baseCenter.add(offX, offY, clickFace == Direction.SOUTH ? 0.5 : -0.5);
            case EAST, WEST -> baseCenter.add(clickFace == Direction.EAST ? 0.5 : -0.5, offY, offZ);
            default -> throw new MatchException(null, null);
         };
         Box baseBox = new Box(base);
         if (!baseBox.contains(eye)) {
            float[] angles = MathUtil.calcAngle(eye, hitVec);
            Vec3d reachEnd = eye.add(lookVector(angles[0], angles[1]).multiply(6.0));
            if (baseBox.raycast(eye, reachEnd).isEmpty()) {
               return null;
            }
         }

         return new BlockHitResult(hitVec, clickFace, base, false);
      } else {
         return null;
      }
   }

   public boolean useFireworks(List<BlockPos> positions, Direction face) {
      return !nullCheck() && !positions.isEmpty() ? Homovore.swapManager.withSwap(Items.FIREWORK_ROCKET, SCOPE, SwapMode.SILENT, 150, () -> {
         for (BlockPos pos : positions) {
            this.send(new BlockHitResult(this.fireworkHit(pos, face), face.getOpposite(), pos.offset(face), false), Hand.MAIN_HAND);
         }
      }) : false;
   }

   private Vec3d fireworkHit(BlockPos pos, Direction face) {
      if (face != Direction.DOWN) {
         return faceCenter(pos, face);
      }

      Vec3d player = mc.player.getEntityPos();
      double x = pos.getX() + (player.x >= pos.getX() + 0.5 ? 0.15 : 0.85);
      double z = pos.getZ() + (player.z >= pos.getZ() + 0.5 ? 0.15 : 0.85);
      return new Vec3d(x, pos.getY(), z);
   }

   @Nullable
   private Direction placeSide(BlockPos pos) {
      Vec3d eye = mc.player.getEyePos();
      Direction best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (Direction side : Direction.values()) {
         BlockPos neighbour = pos.offset(side);
         BlockState state = mc.world.getBlockState(neighbour);
         if (!state.isAir() && state.getFluidState().isEmpty() && !isInteractable(state.getBlock())) {
            double distSq = eye.squaredDistanceTo(Vec3d.ofCenter(neighbour));
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               best = side;
            }
         }
      }

      return best;
   }

   @Nullable
   public BlockHitResult prepareAirPlaceHit(BlockPos pos) {
      if (!this.onCooldown(pos) && PlaceUtil.canPlace(pos)) {
         Vec3d eye = mc.player.getEyePos();
         Direction best = null;
         double bestDistSq = Double.MAX_VALUE;

         for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.offset(side);
            BlockState state = mc.world.getBlockState(neighbour);
            if (!state.isAir() && state.getFluidState().isEmpty() && !isInteractable(state.getBlock())) {
               Vec3d hit = faceCenter(pos, side);
               Vec3d normal = new Vec3d(-side.getOffsetX(), -side.getOffsetY(), -side.getOffsetZ());
               if (!(eye.subtract(hit).dotProduct(normal) <= 0.01)) {
                  double distSq = eye.squaredDistanceTo(hit);
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     best = side;
                  }
               }
            }
         }

         if (best != null) {
            return new BlockHitResult(faceCenter(pos, best), best.getOpposite(), pos.offset(best), false);
         }

         Direction dir = this.airPlaceDirection(pos);
         return new BlockHitResult(faceCenter(pos, dir), dir, pos, false);
      } else {
         return null;
      }
   }

   private Direction airPlaceDirection(BlockPos pos) {
      if (mc.player == null) {
         return Direction.UP;
      }

      Vec3d eye = mc.player.getEyePos();
      Direction best = Direction.UP;
      double bestDist = Double.MAX_VALUE;

      for (Direction d : Direction.values()) {
         double dist = eye.squaredDistanceTo(faceCenter(pos, d));
         if (dist < bestDist) {
            bestDist = dist;
            best = d;
         }
      }

      return best;
   }

   private static Vec3d faceCenter(BlockPos pos, Direction dir) {
      return Vec3d.ofCenter(pos).add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
   }

   private static boolean isInteractable(Block block) {
      return block instanceof BlockWithEntity
         || block instanceof DoorBlock
         || block instanceof TrapdoorBlock
         || block instanceof FenceGateBlock
         || block instanceof ButtonBlock
         || block instanceof LeverBlock
         || block instanceof BedBlock
         || block instanceof NoteBlock
         || block instanceof AnvilBlock;
   }

   private static Vec3d lookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3d(g * h, i, f * h);
   }

   private void send(BlockHitResult hit, Hand hand) {
      PendingUpdateManager prediction = ((ClientLevelAccessor)mc.world).homovore$getBlockStatePredictionHandler().incrementSequence();

      try {
         mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hit, prediction.getSequence()));
      } catch (Throwable var7) {
         if (prediction != null) {
            try {
               prediction.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (prediction != null) {
         prediction.close();
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      Packet<?> packet = event.getPacket();
      if (packet instanceof BlockUpdateS2CPacket bup) {
         this.handleBlockUpdate(bup);
      } else if (packet instanceof BundleS2CPacket bundle) {
         for (Packet<?> sub : bundle.getPackets()) {
            if (sub instanceof BlockUpdateS2CPacket bup) {
               this.handleBlockUpdate(bup);
            }
         }
      }
   }

   private void handleBlockUpdate(BlockUpdateS2CPacket packet) {
      BlockPos pos = packet.getPos().toImmutable();
      boolean nowAir = packet.getState().isAir();
      if (!nowAir) {
         this.cooldowns.remove(pos);
      }

      mc.execute(() -> {
         for (PlacementManager.PlacementListener listener : this.listeners) {
            listener.onBlockUpdate(pos, nowAir);
         }

         this.flushQueue();
      });
   }

   public interface PlacementListener {
      void onBlockUpdate(BlockPos var1, boolean var2);
   }
}
