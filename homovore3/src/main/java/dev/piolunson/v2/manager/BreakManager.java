package dev.piolunson.v2.manager;

import dev.piolunson.v2.event.impl.TickEvent;
import dev.piolunson.v2.event.impl.network.PacketEvent;
import dev.piolunson.v2.event.system.Subscribe;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ObjIntConsumer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class BreakManager {
   private static final int FIRST_STAGE = 0;
   private static final int LAST_STAGE = 9;
   private static final double MINER_REACH = 8.0;
   private static final double STALL_STAGES = 2.5;
   private static final long MIN_STALL_MILLIS = 600L;
   private static final double TICK_MILLIS = 50.0;
   private static final long RESTART_GRACE_MILLIS = 150L;
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private final Map<Integer, BreakManager.Break> started = new ConcurrentHashMap<>();
   private final Map<Integer, BreakManager.Break> cracking = new ConcurrentHashMap<>();
   private ClientWorld level;

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof BlockBreakingProgressS2CPacket packet) {
         BlockPos pos = packet.getPos();
         if (pos != null) {
            int stage = packet.getProgress();
            if (stage >= 0 && stage <= 9) {
               int id = packet.getEntityId();
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

   private static void keepAlive(BreakManager.Break current, BlockPos pos, long now) {
      if (current != null && current.pos.equals(pos)) {
         current.touch(now);
      }
   }

   @Subscribe
   public void onTick(TickEvent.Pre event) {
      if (mc.world != null && mc.player != null) {
         if (mc.world != this.level) {
            this.reset();
            this.level = mc.world;
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
         if (mc.world.getEntityById(entry.getKey()) instanceof PlayerEntity miner && inReach(miner, current.pos)) {
            BlockState state = mc.world.getBlockState(current.pos);
            if (!state.isAir() && !(state.getHardness(mc.world, current.pos) < 0.0F)) {
               current.delta = Math.max(state.calcBlockBreakingDelta(miner, mc.world, current.pos), 0.0F);
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

   private static boolean inReach(PlayerEntity miner, BlockPos pos) {
      return miner.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64.0;
   }

   public boolean isBeingBroken(BlockPos pos, int excludeId) {
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

   public void forEachStarted(ObjIntConsumer<BlockPos> action) {
      this.started.forEach((id, current) -> action.accept(current.pos, id));
   }

   public void forEachSecond(ObjIntConsumer<BlockPos> action) {
      this.cracking.forEach((id, crack) -> {
         BreakManager.Break start = this.started.get(id);
         BlockPos pos = crack.pos;
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
      private volatile BlockPos pos;
      private volatile long startMillis;
      private volatile long updatedMillis;
      private float delta;

      private Break(BlockPos pos, long now) {
         this.pos = pos.toImmutable();
         this.startMillis = now;
         this.updatedMillis = now;
      }

      private void update(BlockPos next, long now) {
         this.touch(now);
         if (!this.pos.equals(next)) {
            this.pos = next.toImmutable();
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
