package dev.leonetic.features.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2588;
import net.minecraft.class_2960;
import net.minecraft.class_465;
import net.minecraft.class_490;
import net.minecraft.class_7923;

public class InstantRekitModule extends Module {
   private final Setting<Boolean> topUpStacks = this.bool("TopUpStacks", true);
   private final Setting<Boolean> closeOnDone = this.bool("CloseOnDone", false);
   public static final String DEFAULT_KIT = "default";
   private final Map<String, Map<Integer, String>> kits = new LinkedHashMap<>();
   private String activeKit = "default";
   private boolean firedThisContainer;

   public InstantRekitModule() {
      super("InstantRekit", "Restores a saved kit from an open container in a single tick.", Module.Category.PLAYER);
   }

   public int saveKit(String name) {
      if (nullCheck()) {
         return 0;
      }

      Map<Integer, String> snapshot = new LinkedHashMap<>();

      for (int slot = 0; slot < 36; slot++) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
         if (!stack.method_7960()) {
            snapshot.put(slot, class_7923.field_41178.method_10221(stack.method_7909()).toString());
         }
      }

      if (snapshot.isEmpty()) {
         return 0;
      }

      this.kits.put(name, snapshot);
      this.activeKit = name;
      return snapshot.size();
   }

   public boolean loadKit(String name) {
      if (!this.kits.containsKey(name)) {
         return false;
      }

      this.activeKit = name;
      this.firedThisContainer = false;
      return true;
   }

   public Set<String> listKits() {
      return this.kits.keySet();
   }

   public boolean hasKit() {
      Map<Integer, String> kit = this.kits.get(this.activeKit);
      return kit != null && !kit.isEmpty();
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (!this.isContainerOpen()) {
            this.firedThisContainer = false;
         } else if (!this.firedThisContainer) {
            this.firedThisContainer = true;
            this.rekit();
         }
      }
   }

   private void rekit() {
      Map<Integer, String> kit = this.kits.get(this.activeKit);
      if (kit == null || kit.isEmpty()) {
         Command.sendMessage("{red} No kit saved. Use .savekit <name> while holding your kit.");
      } else if (InventoryUtil.cursor().method_7960()) {
         for (InstantRekitModule.Move move : this.plan(kit)) {
            this.execute(move);
         }

         if (this.closeOnDone.getValue()) {
            mc.field_1724.method_7346();
         }
      }
   }

   private List<InstantRekitModule.Move> plan(Map<Integer, String> kit) {
      class_1703 menu = mc.field_1724.field_7512;
      int containerCount = Math.max(0, menu.field_7761.size() - 36);
      InstantRekitModule.Usage usage = new InstantRekitModule.Usage(menu, containerCount);
      List<InstantRekitModule.Move> moves = new ArrayList<>();
      Map<Integer, class_1799> freshlyPlaced = new HashMap<>();

      for (Entry<Integer, String> entry : kit.entrySet()) {
         int target = entry.getKey();
         class_1792 want = item(entry.getValue());
         if (want != null) {
            class_1799 cur = mc.field_1724.method_31548().method_5438(target);
            if (cur.method_7960() || cur.method_7909() != want) {
               int src = usage.bestSource(want);
               if (src != -1) {
                  freshlyPlaced.put(target, menu.method_7611(src).method_7677());
                  moves.add(new InstantRekitModule.Move(src, target, false));
                  usage.consumeAll(src);
               }
            }
         }
      }

      if (this.topUpStacks.getValue()) {
         for (Entry<Integer, String> entry : kit.entrySet()) {
            int target = entry.getKey();
            class_1792 want = item(entry.getValue());
            if (want != null) {
               class_1799 cur = freshlyPlaced.getOrDefault(target, mc.field_1724.method_31548().method_5438(target));
               if (!cur.method_7960() && cur.method_7909() == want && cur.method_7946()) {
                  int need = cur.method_7914() - cur.method_7947();
                  int src;
                  if (need > 0) {
                     while (need > 0 && (src = usage.bestSource(want)) != -1) {
                        int avail = usage.remaining(src);
                        moves.add(new InstantRekitModule.Move(src, target, true));
                        int used = Math.min(need, avail);
                        usage.consume(src, used);
                        need -= used;
                     }
                  }
               }
            }
         }
      }

      return moves;
   }

   private void execute(InstantRekitModule.Move move) {
      class_1703 menu = mc.field_1724.field_7512;
      int containerCount = Math.max(0, menu.field_7761.size() - 36);
      if (!move.topUp && move.target <= 8) {
         InventoryUtil.click(move.source, move.target, class_1713.field_7791);
      } else {
         int targetSlot = handlerSlot(move.target, containerCount);
         boolean targetEmpty = menu.method_7611(targetSlot).method_7677().method_7960();
         InventoryUtil.click(move.source, 0, class_1713.field_7790);
         InventoryUtil.click(targetSlot, 0, class_1713.field_7790);
         if (!targetEmpty) {
            InventoryUtil.click(move.source, 0, class_1713.field_7790);
         }
      }
   }

   private static int handlerSlot(int invIndex, int containerCount) {
      return invIndex <= 8 ? containerCount + 27 + invIndex : containerCount + (invIndex - 9);
   }

   private boolean isContainerOpen() {
      if (!(mc.field_1755 instanceof class_465<?> screen && !(mc.field_1755 instanceof class_490))) {
         return false;
      } else {
         if (this.isEnderChest(screen)) {
            return false;
         }

         class_1703 menu = mc.field_1724.field_7512;
         return menu != null && menu.field_7761.size() > 36;
      }
   }

   private boolean isEnderChest(class_465<?> screen) {
      class_2561 title = screen.method_25440();
      return title.method_10851() instanceof class_2588 tc && "container.enderchest".equals(tc.method_11022())
         ? true
         : title.getString().equalsIgnoreCase("Ender Chest");
   }

   private static class_1792 item(String id) {
      if (id == null) {
         return null;
      }

      int colon = id.indexOf(58);
      class_2960 key = colon < 0 ? class_2960.method_60655("minecraft", id) : class_2960.method_60655(id.substring(0, colon), id.substring(colon + 1));
      return (class_1792)class_7923.field_41178.method_63535(key);
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = super.toJson().getAsJsonObject();
      JsonObject kitsObject = new JsonObject();

      for (Entry<String, Map<Integer, String>> kitEntry : this.kits.entrySet()) {
         JsonArray array = new JsonArray();

         for (Entry<Integer, String> entry : kitEntry.getValue().entrySet()) {
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", entry.getKey());
            slot.addProperty("item", entry.getValue());
            array.add(slot);
         }

         kitsObject.add(kitEntry.getKey(), array);
      }

      object.add("Kits", kitsObject);
      object.addProperty("ActiveKit", this.activeKit);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      super.fromJson(element);
      this.kits.clear();
      this.activeKit = "default";
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         JsonElement kitsElement = object.get("Kits");
         if (kitsElement != null && kitsElement.isJsonObject()) {
            for (Entry<String, JsonElement> kitEntry : kitsElement.getAsJsonObject().entrySet()) {
               if (kitEntry.getValue().isJsonArray()) {
                  this.kits.put(kitEntry.getKey(), readKit(kitEntry.getValue().getAsJsonArray()));
               }
            }
         }

         JsonElement legacy = object.get("Kit");
         boolean migratedLegacy = false;
         if (legacy != null && legacy.isJsonArray()) {
            Map<Integer, String> kit = readKit(legacy.getAsJsonArray());
            if (!kit.isEmpty()) {
               migratedLegacy = this.kits.putIfAbsent("default", kit) == null;
            }
         }

         JsonElement active = object.get("ActiveKit");
         if (!migratedLegacy && active != null && active.isJsonPrimitive()) {
            this.activeKit = active.getAsString();
         }

         if (!this.kits.isEmpty() && !this.kits.containsKey(this.activeKit)) {
            this.activeKit = this.kits.containsKey("default") ? "default" : this.kits.keySet().iterator().next();
         }
      }
   }

   private static Map<Integer, String> readKit(JsonArray array) {
      Map<Integer, String> kit = new LinkedHashMap<>();

      for (JsonElement el : array) {
         JsonObject slot = el.getAsJsonObject();
         kit.put(slot.get("slot").getAsInt(), slot.get("item").getAsString());
      }

      return kit;
   }

   private record Move(int source, int target, boolean topUp) {
   }

   private static final class Usage {
      private final class_1703 menu;
      private final int containerCount;
      private final Map<Integer, Integer> remaining = new HashMap<>();

      Usage(class_1703 menu, int containerCount) {
         this.menu = menu;
         this.containerCount = containerCount;
      }

      int remaining(int slot) {
         return this.remaining.computeIfAbsent(slot, i -> this.menu.method_7611(i).method_7677().method_7947());
      }

      int bestSource(class_1792 want) {
         int best = -1;
         int bestCount = 0;

         for (int i = 0; i < this.containerCount; i++) {
            class_1799 s = this.menu.method_7611(i).method_7677();
            if (!s.method_7960() && s.method_7909() == want) {
               int count = this.remaining(i);
               if (count > bestCount) {
                  bestCount = count;
                  best = i;
               }
            }
         }

         return best;
      }

      void consume(int slot, int amount) {
         this.remaining.put(slot, Math.max(0, this.remaining(slot) - amount));
      }

      void consumeAll(int slot) {
         this.remaining.put(slot, 0);
      }
   }
}
