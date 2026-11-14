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
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1542;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2199;
import net.minecraft.class_2237;
import net.minecraft.class_2244;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2269;
import net.minecraft.class_2323;
import net.minecraft.class_2338;
import net.minecraft.class_2349;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2401;
import net.minecraft.class_2428;
import net.minecraft.class_243;
import net.minecraft.class_2533;
import net.minecraft.class_2596;
import net.minecraft.class_2626;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2885;
import net.minecraft.class_3965;
import net.minecraft.class_7202;
import net.minecraft.class_8042;
import net.minecraft.class_2350.class_2351;
import org.jetbrains.annotations.Nullable;

public class PlacementManager extends Feature {
   private static final long WINDOW_MS = 300L;
   private static final int MAX_PER_WINDOW = 9;
   private static final long PER_BLOCK_COOLDOWN_MS = 50L;
   private static final EnumSet<ResultType> SCOPE = EnumSet.of(ResultType.HOTBAR);
   private final Map<class_2338, class_1792> queue = new LinkedHashMap<>();
   private final Deque<Long> window = new ArrayDeque<>();
   private final Map<class_2338, Long> cooldowns = new ConcurrentHashMap<>();
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

   public boolean enqueue(class_2338 pos, class_1792 item) {
      if (pos == null || item == null) {
         return false;
      } else {
         return this.onCooldown(pos) ? false : this.queue.putIfAbsent(pos.method_10062(), item) == null;
      }
   }

   public void removeQueuedFor(Predicate<class_2338> filter) {
      this.queue.keySet().removeIf(filter);
   }

   public void forceResetPlaceCooldown(class_2338 pos) {
      this.cooldowns.remove(pos);
   }

   public void notePlacement(class_2338 pos) {
      long now = System.currentTimeMillis();
      this.cooldowns.put(pos.method_10062(), now);
      this.window.addLast(now);
   }

   private boolean onCooldown(class_2338 pos) {
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
               class_1792 item = this.queue.values().iterator().next();
               List<class_2338> batch = new ArrayList<>();
               List<class_2338> stale = new ArrayList<>();

               for (Entry<class_2338, class_1792> entry : this.queue.entrySet()) {
                  if (entry.getValue() == item) {
                     class_2338 pos = entry.getKey();
                     if (!mc.field_1687.method_8320(pos).method_45474()) {
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

   private boolean place(List<class_2338> batch, class_1792 item, int priority) {
      boolean swapped = Homovore.swapManager.withSwap(item, SCOPE, SwapMode.SILENT, priority, () -> batch.forEach(posx -> this.sendPlace(posx, null)));
      if (!swapped) {
         return false;
      }

      long stamp = System.currentTimeMillis();

      for (class_2338 pos : batch) {
         this.cooldowns.put(pos, stamp);
         this.window.addLast(stamp);
      }

      return true;
   }

   public List<class_2338> placeBatch(List<class_2338> positions, class_1792 item) {
      if (!nullCheck() && !positions.isEmpty() && !EatUtil.shouldDefer()) {
         int budget = this.budget();
         if (budget <= 0) {
            return List.of();
         }

         List<class_2338> batch = new ArrayList<>();

         for (class_2338 pos : positions) {
            if (batch.size() >= budget) {
               break;
            }

            if (!this.onCooldown(pos) && PlaceUtil.canPlace(pos)) {
               batch.add(pos.method_10062());
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

   public boolean placeDirect(class_2338 pos, @Nullable class_2350 face, class_1792 item) {
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

   private void sendPlace(class_2338 pos, @Nullable class_2350 face) {
      class_2350 side = face != null ? face : this.placeSide(pos);
      class_2338 clicked;
      class_243 hitPos;
      class_2350 hitSide;
      if (side == null) {
         hitSide = this.airPlaceDirection(pos);
         clicked = pos;
         hitPos = faceCenter(pos, hitSide);
      } else {
         clicked = pos.method_10093(side);
         hitPos = faceCenter(pos, side);
         hitSide = side.method_10153();
      }

      if (mc.field_1724.method_6047().method_7909() instanceof class_1747 blockItem) {
         class_2680 desired = blockItem.method_7711().method_9564();
         if (desired.method_28498(class_2741.field_12496)) {
            hitSide = switch ((class_2351)desired.method_11654(class_2741.field_12496)) {
               case field_11048 -> class_2350.field_11034;
               case field_11051 -> class_2350.field_11035;
               case field_11052 -> class_2350.field_11036;
               default -> throw new MatchException(null, null);
            };
            hitPos = faceCenter(pos, hitSide);
         }
      }

      this.send(new class_3965(hitPos, hitSide, clicked, false), class_1268.field_5808);
   }

   public boolean placeCrystal(class_2338 base, boolean trustBase) {
      if (!nullCheck() && !EatUtil.shouldDefer()) {
         class_3965 hit = this.crystalHit(base, trustBase);
         return hit == null
            ? false
            : Homovore.swapManager.withSwap(class_1802.field_8301, SCOPE, SwapMode.SILENT, 350, () -> this.send(hit, class_1268.field_5808));
      } else {
         return false;
      }
   }

   @Nullable
   private class_3965 crystalHit(class_2338 base, boolean trustBase) {
      if (!trustBase) {
         class_2680 baseState = mc.field_1687.method_8320(base);
         if (!baseState.method_27852(class_2246.field_10540) && !baseState.method_27852(class_2246.field_9987)) {
            return null;
         }
      }

      class_2338 airPos = base.method_10084();
      class_2680 airState = mc.field_1687.method_8320(airPos);
      if (airState.method_26215() || airState.method_27852(class_2246.field_10036) && mc.field_1687.method_27983().equals(class_1937.field_25181)) {
         for (class_1297 e : mc.field_1687.method_8335(null, new class_238(airPos))) {
            if (!(e instanceof class_1542) && (!(e instanceof class_1511 crystal) || crystal.field_6012 >= 5 && !crystal.method_24515().equals(airPos))) {
               return null;
            }
         }

         class_243 eye = mc.field_1724.method_5836(1.0F);
         class_243 baseCenter = class_243.method_24953(base);
         double dx = eye.field_1352 - baseCenter.field_1352;
         double dy = eye.field_1351 - baseCenter.field_1351;
         double dz = eye.field_1350 - baseCenter.field_1350;
         double absX = Math.abs(dx);
         double absY = Math.abs(dy);
         double absZ = Math.abs(dz);
         class_2350 clickFace;
         if (absY >= absX && absY >= absZ) {
            clickFace = dy > 0.0 ? class_2350.field_11036 : class_2350.field_11033;
         } else if (absX >= absZ) {
            clickFace = dx > 0.0 ? class_2350.field_11034 : class_2350.field_11039;
         } else {
            clickFace = dz > 0.0 ? class_2350.field_11035 : class_2350.field_11043;
         }

         class_243 playerPos = mc.field_1724.method_73189();
         double offX = Math.clamp(playerPos.field_1352 - Math.floor(playerPos.field_1352), 0.2, 0.8) - 0.5;
         double offY = Math.clamp(playerPos.field_1351 - Math.floor(playerPos.field_1351), 0.2, 0.8) - 0.5;
         double offZ = Math.clamp(playerPos.field_1350 - Math.floor(playerPos.field_1350), 0.2, 0.8) - 0.5;

         class_243 hitVec = switch (clickFace) {
            case field_11036, field_11033 -> baseCenter.method_1031(offX, clickFace == class_2350.field_11036 ? 0.5 : -0.5, offZ);
            case field_11043, field_11035 -> baseCenter.method_1031(offX, offY, clickFace == class_2350.field_11035 ? 0.5 : -0.5);
            case field_11034, field_11039 -> baseCenter.method_1031(clickFace == class_2350.field_11034 ? 0.5 : -0.5, offY, offZ);
            default -> throw new MatchException(null, null);
         };
         class_238 baseBox = new class_238(base);
         if (!baseBox.method_1006(eye)) {
            float[] angles = MathUtil.calcAngle(eye, hitVec);
            class_243 reachEnd = eye.method_1019(lookVector(angles[0], angles[1]).method_1021(6.0));
            if (baseBox.method_992(eye, reachEnd).isEmpty()) {
               return null;
            }
         }

         return new class_3965(hitVec, clickFace, base, false);
      } else {
         return null;
      }
   }

   public boolean useFireworks(List<class_2338> positions, class_2350 face) {
      return !nullCheck() && !positions.isEmpty() ? Homovore.swapManager.withSwap(class_1802.field_8639, SCOPE, SwapMode.SILENT, 150, () -> {
         for (class_2338 pos : positions) {
            this.send(new class_3965(this.fireworkHit(pos, face), face.method_10153(), pos.method_10093(face), false), class_1268.field_5808);
         }
      }) : false;
   }

   private class_243 fireworkHit(class_2338 pos, class_2350 face) {
      if (face != class_2350.field_11033) {
         return faceCenter(pos, face);
      }

      class_243 player = mc.field_1724.method_73189();
      double x = pos.method_10263() + (player.field_1352 >= pos.method_10263() + 0.5 ? 0.15 : 0.85);
      double z = pos.method_10260() + (player.field_1350 >= pos.method_10260() + 0.5 ? 0.15 : 0.85);
      return new class_243(x, pos.method_10264(), z);
   }

   @Nullable
   private class_2350 placeSide(class_2338 pos) {
      class_243 eye = mc.field_1724.method_33571();
      class_2350 best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (class_2350 side : class_2350.values()) {
         class_2338 neighbour = pos.method_10093(side);
         class_2680 state = mc.field_1687.method_8320(neighbour);
         if (!state.method_26215() && state.method_26227().method_15769() && !isInteractable(state.method_26204())) {
            double distSq = eye.method_1025(class_243.method_24953(neighbour));
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               best = side;
            }
         }
      }

      return best;
   }

   @Nullable
   public class_3965 prepareAirPlaceHit(class_2338 pos) {
      if (!this.onCooldown(pos) && PlaceUtil.canPlace(pos)) {
         class_243 eye = mc.field_1724.method_33571();
         class_2350 best = null;
         double bestDistSq = Double.MAX_VALUE;

         for (class_2350 side : class_2350.values()) {
            class_2338 neighbour = pos.method_10093(side);
            class_2680 state = mc.field_1687.method_8320(neighbour);
            if (!state.method_26215() && state.method_26227().method_15769() && !isInteractable(state.method_26204())) {
               class_243 hit = faceCenter(pos, side);
               class_243 normal = new class_243(-side.method_10148(), -side.method_10164(), -side.method_10165());
               if (!(eye.method_1020(hit).method_1026(normal) <= 0.01)) {
                  double distSq = eye.method_1025(hit);
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     best = side;
                  }
               }
            }
         }

         if (best != null) {
            return new class_3965(faceCenter(pos, best), best.method_10153(), pos.method_10093(best), false);
         }

         class_2350 dir = this.airPlaceDirection(pos);
         return new class_3965(faceCenter(pos, dir), dir, pos, false);
      } else {
         return null;
      }
   }

   private class_2350 airPlaceDirection(class_2338 pos) {
      if (mc.field_1724 == null) {
         return class_2350.field_11036;
      }

      class_243 eye = mc.field_1724.method_33571();
      class_2350 best = class_2350.field_11036;
      double bestDist = Double.MAX_VALUE;

      for (class_2350 d : class_2350.values()) {
         double dist = eye.method_1025(faceCenter(pos, d));
         if (dist < bestDist) {
            bestDist = dist;
            best = d;
         }
      }

      return best;
   }

   private static class_243 faceCenter(class_2338 pos, class_2350 dir) {
      return class_243.method_24953(pos).method_1031(dir.method_10148() * 0.5, dir.method_10164() * 0.5, dir.method_10165() * 0.5);
   }

   private static boolean isInteractable(class_2248 block) {
      return block instanceof class_2237
         || block instanceof class_2323
         || block instanceof class_2533
         || block instanceof class_2349
         || block instanceof class_2269
         || block instanceof class_2401
         || block instanceof class_2244
         || block instanceof class_2428
         || block instanceof class_2199;
   }

   private static class_243 lookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new class_243(g * h, i, f * h);
   }

   private void send(class_3965 hit, class_1268 hand) {
      class_7202 prediction = ((ClientLevelAccessor)mc.field_1687).homovore$getBlockStatePredictionHandler().method_41937();

      try {
         mc.method_1562().method_52787(new class_2885(hand, hit, prediction.method_41942()));
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
      class_2596<?> packet = event.getPacket();
      if (packet instanceof class_2626 bup) {
         this.handleBlockUpdate(bup);
      } else if (packet instanceof class_8042 bundle) {
         for (class_2596<?> sub : bundle.method_48324()) {
            if (sub instanceof class_2626 bup) {
               this.handleBlockUpdate(bup);
            }
         }
      }
   }

   private void handleBlockUpdate(class_2626 packet) {
      class_2338 pos = packet.method_11309().method_10062();
      boolean nowAir = packet.method_11308().method_26215();
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
      void onBlockUpdate(class_2338 var1, boolean var2);
   }
}
