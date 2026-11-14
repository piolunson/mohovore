package dev.leonetic.manager;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.Feature;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.inventory.SwapMode;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;

public class SwapManager extends Feature {
   public static boolean logging;
   private static final StackWalker WALKER = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);
   private static final int SILENT_BUDGET = 3;
   private SwapManager.HeldSwap held;
   private int silentUsed;
   private int reserved = Integer.MIN_VALUE;
   private int starved = Integer.MIN_VALUE;
   private int serverSlot = -1;

   public void init() {
      EVENT_BUS.register(this);
   }

   public boolean withSwap(Result target, SwapMode mode, Runnable action) {
      return this.withSwap(target, mode, 0, action);
   }

   public boolean withSwap(Result target, SwapMode mode, int priority, Runnable action) {
      if (mode == SwapMode.LATCH) {
         throw new IllegalArgumentException("LATCH is held, not run: use latch()");
      }

      if (!this.onClientThread()) {
         mc.execute(() -> this.withSwap(target, mode, priority, action));
         return false;
      }

      Result resolved = this.resolve(target, mode);
      if (resolved != null && resolved.found()) {
         if (resolved.holding()) {
            if (logging) {
               this.log("swap {} {} caller={} -> already holding", mode, describe(resolved), caller());
            }

            action.run();
            return true;
         } else {
            boolean silent = mode == SwapMode.SILENT;
            if (silent && !this.claimSilent(priority)) {
               if (logging) {
                  this.log(
                     "swap SILENT {} priority={} caller={} -> out of budget ({}/{} used, reserved={})",
                     describe(resolved),
                     priority,
                     caller(),
                     this.silentUsed,
                     3,
                     this.reserved
                  );
               }

               return false;
            } else {
               if (silent && this.interruptLatch()) {
                  resolved = this.resolve(resolved, mode);
                  if (resolved == null || !resolved.found()) {
                     if (logging) {
                        this.log("swap {} caller={} -> no result after latch interrupt", mode, caller());
                     }

                     return false;
                  }

                  if (resolved.holding()) {
                     if (logging) {
                        this.log("swap {} {} caller={} -> already holding (latch released)", mode, describe(resolved), caller());
                     }

                     action.run();
                     return true;
                  }
               }

               if (!this.permitted(mode)) {
                  if (logging) {
                     this.log("swap {} {} caller={} -> blocked (using item)", mode, describe(resolved), caller());
                  }

                  return false;
               } else {
                  int last = InventoryUtil.selected();
                  if (!mode.strategy().swap(resolved)) {
                     if (logging) {
                        this.log("swap {} {} caller={} -> strategy refused", mode, describe(resolved), caller());
                     }

                     return false;
                  } else {
                     try {
                        action.run();
                     } finally {
                        mode.strategy().swapBack(last, resolved);
                     }

                     if (silent) {
                        this.commitSilent(priority);
                     }

                     if (logging) {
                        this.log("swap {} {} from={} caller={} -> done", mode, describe(resolved), last, caller());
                     }

                     return true;
                  }
               }
            }
         }
      } else {
         if (logging) {
            this.log("swap {} caller={} -> no result", mode, caller());
         }

         return false;
      }
   }

   public boolean withSwap(Item item, SwapMode mode, Runnable action) {
      return this.withSwap(item, mode.scope(), mode, 0, action);
   }

   public boolean withSwap(Item item, SwapMode mode, int priority, Runnable action) {
      return this.withSwap(item, mode.scope(), mode, priority, action);
   }

   public boolean withSwap(Item item, EnumSet<ResultType> scope, SwapMode mode, int priority, Runnable action) {
      return this.withSwap(InventoryUtil.find(item, scope), mode, priority, action);
   }

   public boolean withSwap(Predicate<ItemStack> predicate, SwapMode mode, int priority, Runnable action) {
      return this.withSwap(InventoryUtil.find(predicate, mode.scope()), mode, priority, action);
   }

   public boolean latch(Result target) {
      if (!this.onClientThread()) {
         return false;
      }

      Result result = this.resolve(target, SwapMode.LATCH);
      if (result != null && result.found()) {
         if (result.holding()) {
            return true;
         }

         if (this.held != null) {
            if (logging) {
               this.log("latch {} caller={} -> busy (held {} ticks)", describe(result), caller(), this.held.ticksHeld);
            }

            return false;
         } else if (!this.permitted(SwapMode.LATCH)) {
            if (logging) {
               this.log("latch {} caller={} -> blocked (using item)", describe(result), caller());
            }

            return false;
         } else {
            int last = InventoryUtil.selected();
            if (!SwapMode.LATCH.strategy().swap(result)) {
               if (logging) {
                  this.log("latch {} caller={} -> strategy refused", describe(result), caller());
               }

               return false;
            } else {
               this.held = new SwapManager.HeldSwap(result, last);
               if (logging) {
                  this.log("latch {} from={} caller={}", describe(result), last, caller());
               }

               return true;
            }
         }
      } else {
         if (logging) {
            this.log("latch caller={} -> no result", caller());
         }

         return false;
      }
   }

   public boolean latch(Item item) {
      return this.latch(InventoryUtil.find(item, SwapMode.LATCH.scope()));
   }

   public void release() {
      if (this.held != null) {
         if (logging) {
            this.log(
               "release {} back={} after {} ticks{}",
               describe(this.held.result),
               this.held.last,
               this.held.ticksHeld,
               nullCheck() ? " (no player, dropped)" : ""
            );
         }

         if (!nullCheck()) {
            SwapMode.LATCH.strategy().swapBack(this.held.last, this.held.result);
         }

         this.held = null;
      }
   }

   public boolean isLatched() {
      return this.held != null;
   }

   public int latchedSlot() {
      return this.held == null ? -1 : this.held.result.slot();
   }

   private boolean interruptLatch() {
      if (this.held == null) {
         return false;
      }

      if (logging) {
         this.log("latch interrupted by silent swap after {} ticks", this.held.ticksHeld);
      }

      this.release();
      if (!nullCheck() && mc.player.isUsingItem()) {
         mc.player.clearActiveItem();
      }

      return true;
   }

   private boolean claimSilent(int priority) {
      int available = priority < this.reserved ? 2 : 3;
      if (this.silentUsed >= available) {
         if (priority > this.starved) {
            this.starved = priority;
         }

         return false;
      } else {
         return true;
      }
   }

   private void commitSilent(int priority) {
      this.silentUsed++;
      if (priority >= this.reserved) {
         this.reserved = Integer.MIN_VALUE;
      }
   }

   private boolean permitted(SwapMode mode) {
      if (nullCheck()) {
         return false;
      } else {
         return mode == SwapMode.ALTSILENT ? true : !mc.player.isUsingItem();
      }
   }

   @Subscribe(priority = 2000)
   private void onPreTick(PreTickEvent event) {
      this.repinLatch();
      this.silentUsed = 0;
      this.reserved = this.starved;
      this.starved = Integer.MIN_VALUE;
   }

   private void repinLatch() {
      if (this.held != null) {
         if (nullCheck()) {
            if (logging) {
               this.log("latch dropped: no player/world");
            }

            this.held = null;
         } else {
            this.held.ticksHeld++;
            if (InventoryUtil.selected() != this.held.result.slot()) {
               if (logging) {
                  this.log("re-pin latch slot {} (was {}) after {} ticks", this.held.result.slot(), InventoryUtil.selected(), this.held.ticksHeld);
               }

               InventoryUtil.swap(this.held.result.slot());
            }
         }
      }
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
         this.serverSlot = packet.getSelectedSlot();
      }
   }

   public int serverSlot() {
      if (mc.player == null) {
         return this.serverSlot;
      } else {
         return this.serverSlot == -1 ? InventoryUtil.selected() : this.serverSlot;
      }
   }

   private Result resolve(Result result, SwapMode mode) {
      if (result != null && result.found() && !nullCheck()) {
         if (result.type() == ResultType.OFFHAND) {
            if (mc.player.getOffHandStack() == result.stack()) {
               return result;
            }
         } else {
            ItemStack live = mc.player.getInventory().getStack(result.slot());
            if (live == result.stack() && !live.isEmpty()) {
               return new Result(result.slot(), live, result.type());
            }
         }

         if (result.stack().isEmpty()) {
            return null;
         }

         Result found = InventoryUtil.find(result.stack().getItem(), mode.scope());
         if (logging && found.found() && found.slot() != result.slot()) {
            this.log("resolve {} moved {}@{} -> {} caller={}", mode, Registries.ITEM.getId(result.stack().getItem()), result.slot(), found.slot(), caller());
         }

         return found;
      } else {
         return result;
      }
   }

   private boolean onClientThread() {
      return mc.isOnThread();
   }

   private static String describe(Result result) {
      return Registries.ITEM.getId(result.stack().getItem()) + "@" + result.slot() + "(" + result.type() + ")";
   }

   private static String caller() {
      return WALKER.walk(
         frames -> frames.map(StackFrame::getDeclaringClass).filter(c -> c != SwapManager.class).map(Class::getSimpleName).findFirst().orElse("?")
      );
   }

   private void log(String format, Object... args) {
      Object[] all = new Object[args.length + 1];
      all[0] = nullCheck() ? -1 : mc.player.age;
      System.arraycopy(args, 0, all, 1, args.length);
      Homovore.LOGGER.info("[Swap] tick={} " + format, all);
   }

   private static final class HeldSwap {
      private final Result result;
      private final int last;
      private int ticksHeld;

      private HeldSwap(Result result, int last) {
         this.result = result;
         this.last = last;
      }
   }
}
