package dev.leonetic.manager;

import dev.leonetic.event.impl.TickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ObjIntConsumer;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2620;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_638;

public final class BreakManager {
   private static final int FIRST_STAGE = 0;
   private static final int LAST_STAGE = 9;
   private static final double MINER_REACH = 8.0;
   private static final double STALL_STAGES = 2.5;
   private static final long MIN_STALL_MILLIS = 600L;
   private static final double TICK_MILLIS = 50.0;
   private static final long RESTART_GRACE_MILLIS = 150L;
   private static final class_310 mc = class_310.method_1551();
   private final Map<Integer, BreakManager.Break> started = new ConcurrentHashMap<>();
   private final Map<Integer, BreakManager.Break> cracking = new ConcurrentHashMap<>();
   private class_638 level;

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof class_2620 packet) {
         class_2338 pos = packet.method_11277();
         if (pos != null) {
            int stage = packet.method_11278();
            if (stage >= 0 && stage <= 9) {
               int id = packet.method_11280();
               long now = System.currentTimeMillis();
               keepAlive(this.started.get(id), pos, now);
               if (stage > 0) {
                  BreakManager.Break crack = this.cracking.get(id);
                  if (crack == null) {
                     this.cracking.put(id, new BreakManager.Break(pos, now));
                  } else {
                     crack.update(pos, now);
                  }
               } else {
                  BreakManager.Break crack = this.cracking.get(id);
                  if (crack != null && crack.pos.equals(pos)) {
                     crack.touch(now);
                     if (now - crack.startMillis < 150L) {
                        return;
                     }
                  }

                  BreakManager.Break current = this.started.get(id);
                  if (current == null || !current.pos.equals(pos)) {
                     this.started.put(id, new BreakManager.Break(pos, now));
                     if (crack == null) {
                        this.cracking.put(id, new BreakManager.Break(pos, now));
                     }
                  }
               }
            }
         }
      }
   }

   private static void keepAlive(BreakManager.Break current, class_2338 pos, long now) {
      if (current != null && current.pos.equals(pos)) {
         current.touch(now);
      }
   }

   @Subscribe
   public void onTick(TickEvent.Pre event) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (mc.field_1687 != this.level) {
            this.reset();
            this.level = mc.field_1687;
         }

         long now = System.currentTimeMillis();
         this.prune(this.started.entrySet().iterator(), now);
         this.prune(this.cracking.entrySet().iterator(), now);
      } else {
         this.reset();
      }
   }

   private void prune(Iterator<Entry<Integer, BreakManager.Break>> entries, long now) {
      while (entries.hasNext()) {
         Entry<Integer, BreakManager.Break> entry = entries.next();
         BreakManager.Break current = entry.getValue();
         if (mc.field_1687.method_8469(entry.getKey()) instanceof class_1657 miner && inReach(miner, current.pos)) {
            class_2680 state = mc.field_1687.method_8320(current.pos);
            if (!state.method_26215() && !(state.method_26214(mc.field_1687, current.pos) < 0.0F)) {
               current.delta = Math.max(state.method_26165(miner, mc.field_1687, current.pos), 0.0F);
               if (now - current.updatedMillis > current.stallMillis()) {
                  entries.remove();
               }
            } else {
               entries.remove();
            }
         } else {
            entries.remove();
         }
      }
   }

   private static boolean inReach(class_1657 miner, class_2338 pos) {
      return miner.method_33571().method_1025(class_243.method_24953(pos)) <= 64.0;
   }

   public boolean isBeingBroken(class_2338 pos, int excludeId) {
      for (Entry<Integer, BreakManager.Break> entry : this.started.entrySet()) {
         if (entry.getKey() != excludeId && entry.getValue().pos.equals(pos)) {
            return true;
         }
      }

      for (Entry<Integer, BreakManager.Break> entry : this.cracking.entrySet()) {
         if (entry.getKey() != excludeId && entry.getValue().pos.equals(pos)) {
            return true;
         }
      }

      return false;
   }

   public void forEachStarted(ObjIntConsumer<class_2338> action) {
      this.started.forEach((id, current) -> action.accept(current.pos, id));
   }

   public void forEachSecond(ObjIntConsumer<class_2338> action) {
      this.cracking.forEach((id, crack) -> {
         BreakManager.Break start = this.started.get(id);
         class_2338 pos = crack.pos;
         if (start == null || !start.pos.equals(pos)) {
            action.accept(pos, id);
         }
      });
   }

   public void reset() {
      this.started.clear();
      this.cracking.clear();
      this.level = null;
   }

   private static final class Break {
      private volatile class_2338 pos;
      private volatile long startMillis;
      private volatile long updatedMillis;
      private float delta;

      private Break(class_2338 pos, long now) {
         this.pos = pos.method_10062();
         this.startMillis = now;
         this.updatedMillis = now;
      }

      private void update(class_2338 next, long now) {
         this.touch(now);
         if (!this.pos.equals(next)) {
            this.pos = next.method_10062();
            this.startMillis = now;
         }
      }

      private void touch(long now) {
         this.updatedMillis = now;
      }

      private long stallMillis() {
         float current = this.delta;
         double stage = current > 0.0F ? 50.0 / current / 10.0 : 0.0;
         return Math.max(600L, (long)(stage * 2.5));
      }
   }
}
