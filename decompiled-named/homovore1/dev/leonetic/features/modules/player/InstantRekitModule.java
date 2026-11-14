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
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

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
         ItemStack stack = mc.player.getInventory().getStack(slot);
         if (!stack.isEmpty()) {
            snapshot.put(slot, Registries.ITEM.getId(stack.getItem()).toString());
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
      } else if (InventoryUtil.cursor().isEmpty()) {
         for (InstantRekitModule.Move move : this.plan(kit)) {
            this.execute(move);
         }

         if (this.closeOnDone.getValue()) {
            mc.player.closeHandledScreen();
         }
      }
   }

   private List<InstantRekitModule.Move> plan(Map<Integer, String> kit) {
      ScreenHandler menu = mc.player.currentScreenHandler;
      int containerCount = Math.max(0, menu.slots.size() - 36);
      InstantRekitModule.Usage usage = new InstantRekitModule.Usage(menu, containerCount);
      List<InstantRekitModule.Move> moves = new ArrayList<>();
      Map<Integer, ItemStack> freshlyPlaced = new HashMap<>();

      for (Entry<Integer, String> entry : kit.entrySet()) {
         int target = entry.getKey();
         Item want = item(entry.getValue());
         if (want != null) {
            ItemStack cur = mc.player.getInventory().getStack(target);
            if (cur.isEmpty() || cur.getItem() != want) {
               int src = usage.bestSource(want);
               if (src != -1) {
                  freshlyPlaced.put(target, menu.getSlot(src).getStack());
                  moves.add(new InstantRekitModule.Move(src, target, false));
                  usage.consumeAll(src);
               }
            }
         }
      }

      if (this.topUpStacks.getValue()) {
         for (Entry<Integer, String> entry : kit.entrySet()) {
            int target = entry.getKey();
            Item want = item(entry.getValue());
            if (want != null) {
               ItemStack cur = freshlyPlaced.getOrDefault(target, mc.player.getInventory().getStack(target));
               if (!cur.isEmpty() && cur.getItem() == want && cur.isStackable()) {
                  int need = cur.getMaxCount() - cur.getCount();
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
      ScreenHandler menu = mc.player.currentScreenHandler;
      int containerCount = Math.max(0, menu.slots.size() - 36);
      if (!move.topUp && move.target <= 8) {
         InventoryUtil.click(move.source, move.target, SlotActionType.SWAP);
      } else {
         int targetSlot = handlerSlot(move.target, containerCount);
         boolean targetEmpty = menu.getSlot(targetSlot).getStack().isEmpty();
         InventoryUtil.click(move.source, 0, SlotActionType.PICKUP);
         InventoryUtil.click(targetSlot, 0, SlotActionType.PICKUP);
         if (!targetEmpty) {
            InventoryUtil.click(move.source, 0, SlotActionType.PICKUP);
         }
      }
   }

   private static int handlerSlot(int invIndex, int containerCount) {
      return invIndex <= 8 ? containerCount + 27 + invIndex : containerCount + (invIndex - 9);
   }

   private boolean isContainerOpen() {
      if (!(mc.currentScreen instanceof HandledScreen<?> screen && !(mc.currentScreen instanceof InventoryScreen))) {
         return false;
      } else {
         if (this.isEnderChest(screen)) {
            return false;
         }

         ScreenHandler menu = mc.player.currentScreenHandler;
         return menu != null && menu.slots.size() > 36;
      }
   }

   private boolean isEnderChest(HandledScreen<?> screen) {
      Text title = screen.getTitle();
      return title.getContent() instanceof TranslatableTextContent tc && "container.enderchest".equals(tc.getKey())
         ? true
         : title.getString().equalsIgnoreCase("Ender Chest");
   }

   private static Item item(String id) {
      if (id == null) {
         return null;
      }

      int colon = id.indexOf(58);
      Identifier key = colon < 0 ? Identifier.of("minecraft", id) : Identifier.of(id.substring(0, colon), id.substring(colon + 1));
      return (Item)Registries.ITEM.get(key);
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
      private final ScreenHandler menu;
      private final int containerCount;
      private final Map<Integer, Integer> remaining = new HashMap<>();

      Usage(ScreenHandler menu, int containerCount) {
         this.menu = menu;
         this.containerCount = containerCount;
      }

      int remaining(int slot) {
         return this.remaining.computeIfAbsent(slot, i -> this.menu.getSlot(i).getStack().getCount());
      }

      int bestSource(Item want) {
         int best = -1;
         int bestCount = 0;

         for (int i = 0; i < this.containerCount; i++) {
            ItemStack s = this.menu.getSlot(i).getStack();
            if (!s.isEmpty() && s.getItem() == want) {
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
