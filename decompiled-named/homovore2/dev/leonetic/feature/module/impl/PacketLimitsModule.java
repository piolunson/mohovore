package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.network.PacketLimitSettings;
import java.util.concurrent.TimeUnit;

public final class PacketLimitsModule extends Module {
   private static PacketLimitsModule instance;
   public final Setting<Boolean> limit = this.bool("Limit", true)
      .describe("Bail out of an action when it would cross a server-side packet limit. The module itself must also be enabled.");
   public final Setting<Integer> globalWindow = this.integer("GlobalWindow", 3951, 500, 20000)
      .describe("Rolling window in ms for every outgoing packet, vanilla traffic included.")
      .page("Global");
   public final Setting<Integer> globalLimit = this.integer("GlobalLimit", 1249, 50, 4000)
      .describe("Packets allowed inside that window before the server kicks.")
      .page("Global");
   public final Setting<Boolean> grimTickGate = this.bool("GrimTickGate", true)
      .describe("One distinct break and entity interaction per client tick; placement is intentionally not tracked or gated.")
      .page("Global");
   public final Setting<Integer> useItemWindow = this.integer("UseItemWindow", 300, 50, 2000)
      .describe("Window in ms. Paper's spam-limiter drops the tenth use-item packet per 300ms.")
      .page("UseItem");
   public final Setting<Integer> useItemLimit = this.integer("UseItemLimit", 9, 1, 40)
      .describe("Shared by UseItemOn and UseItem, exactly as Spigot's counter shares them.")
      .page("UseItem");
   public final Setting<Integer> placeRecipeWindow = this.integer("RecipeWindow", 4200, 50, 20000)
      .describe("Window in ms for PlaceRecipe, Paper's one default per-packet override.")
      .page("Recipe");
   public final Setting<Integer> placeRecipeLimit = this.integer("RecipeLimit", 19, 1, 200)
      .describe("Recipe-book placements allowed inside that window before they are dropped.")
      .page("Recipe");
   public final Setting<Integer> clickSlotWindow = this.integer("ClickSlotWindow", 4200, 50, 20000)
      .describe(
         "Rolling window in ms. Anticheat-derived; Paper does not limit inventory clicks. Keep it longer than the server's own window so a burst cannot straddle two of ours and land inside one of the server's."
      )
      .page("ClickSlot");
   public final Setting<Integer> clickSlotLimit = this.integer("ClickSlotLimit", 72, 1, 500)
      .describe(
         "Clicks allowed inside that window. Keep it well under the server's own click limit, which is commonly 82 per 4000ms: a protocol translator and your own manual clicks also land in that window."
      )
      .page("ClickSlot");
   public final Setting<Integer> offhandWindow = this.integer("OffhandWindow", 500, 50, 5000)
      .describe("Rolling window in ms. Anticheat-derived; Paper does not limit offhand swaps.")
      .page("Offhand");
   public final Setting<Integer> offhandLimit = this.integer("OffhandLimit", 151, 1, 500).page("Offhand");
   public final Setting<Boolean> tpsSync = this.bool("TpsSync", false)
      .describe("Widen anticheat-derived windows when the server lags. Paper's wall-clock use-item window is never stretched.")
      .page("TpsSync");
   public final Setting<Double> tpsThreshold = this.number("TpsThreshold", (double)18.0, (double)1.0, (double)20.0)
      .describe("Start stretching once TPS falls to this.")
      .visibleWhen(() -> this.tpsSync.getValue())
      .page("TpsSync");
   public final Setting<Double> maxStretch = this.number("MaxStretch", (double)2.0, (double)1.0, (double)5.0)
      .describe("Most a window may be widened by. The factor is 20/TPS, so 2.0 is reached at 10 TPS and anything slower is capped there.")
      .visibleWhen(() -> this.tpsSync.getValue())
      .page("TpsSync");

   public PacketLimitsModule() {
      super("PacketLimits", Category.CLIENT, "Global outgoing packet budget for a Paper + Grim server.");
      instance = this;
      this.publish();
   }

   public static PacketLimitsModule get() {
      return instance;
   }

   @Override
   public void onEnable() {
      this.publish();
   }

   @Override
   public void onDisable() {
      this.publish();
   }

   public void publish() {
      PacketLimitSettings.set(
         new PacketLimitSettings.Values(
            this.isEnabled() && this.limit.getValue(),
            TimeUnit.MILLISECONDS.toNanos(this.globalWindow.getValue().intValue()),
            this.globalLimit.getValue(),
            TimeUnit.MILLISECONDS.toNanos(this.useItemWindow.getValue().intValue()),
            this.useItemLimit.getValue(),
            TimeUnit.MILLISECONDS.toNanos(this.clickSlotWindow.getValue().intValue()),
            this.clickSlotLimit.getValue(),
            TimeUnit.MILLISECONDS.toNanos(this.offhandWindow.getValue().intValue()),
            this.offhandLimit.getValue(),
            TimeUnit.MILLISECONDS.toNanos(this.placeRecipeWindow.getValue().intValue()),
            this.placeRecipeLimit.getValue(),
            this.grimTickGate.getValue(),
            this.tpsSync.getValue(),
            this.tpsThreshold.getValue(),
            this.maxStretch.getValue()
         )
      );
   }
}
