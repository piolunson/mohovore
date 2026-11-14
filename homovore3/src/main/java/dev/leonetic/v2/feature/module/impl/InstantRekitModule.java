package dev.leonetic.v2.feature.module.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.ButtonSetting;
import dev.leonetic.v2.feature.setting.ListSetting;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.util.ChatUtil;
import dev.leonetic.v2.util.inventory.ContainerClicks;
import dev.leonetic.v2.util.inventory.InventorySnapshot;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

public final class InstantRekitModule extends Module {
   public static final String DEFAULT_KIT = "default";
   public final ListSetting kit = this.list("Kit", "default", this::kitNames);
   public final Setting<String> kitName = this.string("Kit Name", "default");
   public final ButtonSetting saveCurrent = this.button("Save Current Kit", this::saveFromSetting);
   public final ButtonSetting deleteSelected = this.button("Delete Selected Kit", this::deleteFromSetting);
   public final Setting<Boolean> topUpStacks = this.bool("TopUpStacks", true)
      .describe("Fill saved stackable slots toward their maximum after restoring the layout.");
   public final Setting<Boolean> closeOnDone = this.bool("CloseOnDone", true).describe("Close the container after the complete click plan is applied.");
   private final Map<String, Map<Integer, String>> kits = new LinkedHashMap<>();
   private final AtomicBoolean pending = new AtomicBoolean();
   private int openContainerId = -1;
   private boolean firedThisContainer;

   public InstantRekitModule() {
      super("InstantRekit", Category.EXTRA, "Restores a selected saved kit from an open container in one transaction.");
      this.kit.describe("The saved layout to restore when a container opens.");
      this.kitName.describe("Name used by Save Current Kit; saving an existing name replaces it.");
      this.kit.onChange(this::arm);
   }

   @Override
   public void onEnable() {
      this.arm();
   }

   @Override
   public void onDisable() {
      this.openContainerId = -1;
      this.firedThisContainer = false;
   }

   @Override
   public void onTick() {
      ScreenHandler menu = this.rekitMenu();
      if (menu == null) {
         this.openContainerId = -1;
         this.firedThisContainer = false;
      } else {
         if (menu.syncId != this.openContainerId) {
            this.openContainerId = menu.syncId;
            this.firedThisContainer = false;
         }

         if (!this.firedThisContainer && !this.pending.get()) {
            Map<Integer, String> selected = this.selectedKit();
            if (selected == null || selected.isEmpty()) {
               this.firedThisContainer = true;
               ChatUtil.send("{red}No kit named %s is saved. Use .savekit <name> or Save Current Kit.", this.kit.getValue());
            } else if (menu.getCursorStack().isEmpty()) {
               InstantRekitModule.ClickPlan plan = this.plan(menu, selected);
               this.firedThisContainer = true;
               if (plan.clicks().isEmpty()) {
                  if (this.closeOnDone.getValue() && mc.player != null && mc.player.currentScreenHandler.syncId == menu.syncId) {
                     mc.player.closeHandledScreen();
                  }
               } else {
                  this.pending.set(true);
                  int containerId = menu.syncId;
                  Runnable close = this.closeOnDone.getValue() ? () -> {
                     if (mc.player != null && mc.player.currentScreenHandler.syncId == containerId) {
                        mc.player.closeHandledScreen();
                     }
                  } : null;
                  ContainerClicks.submitPlan(containerId, plan.stateId(), plan.clicks(), close).whenComplete((applied, failure) -> {
                     this.pending.set(false);
                     if (failure != null || !Boolean.TRUE.equals(applied)) {
                        ContainerClicks.dispatch(() -> {
                           if (mc.player != null && mc.player.currentScreenHandler.syncId == containerId) {
                              this.firedThisContainer = false;
                           }
                        });
                     }
                  });
               }
            }
         }
      }
   }

   public synchronized int saveKit(String name) {
      String normalized = normalizeName(name);
      if (normalized == null) {
         return 0;
      }

      InventorySnapshot inventory = InventoryUtil.snapshot();
      if (!inventory.available()) {
         return 0;
      }

      Map<Integer, String> saved = new LinkedHashMap<>();

      for (int slot = 0; slot < Math.min(36, inventory.size()); slot++) {
         ItemStack stack = inventory.item(slot);
         if (!stack.isEmpty()) {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id != null) {
               saved.put(slot, id.toString());
            }
         }
      }

      if (saved.isEmpty()) {
         return 0;
      }

      this.kits.put(normalized, Collections.unmodifiableMap(new LinkedHashMap<>(saved)));
      this.kit.setValue(normalized);
      this.kitName.setValue(normalized);
      this.arm();
      return saved.size();
   }

   public synchronized boolean selectKit(String name) {
      String normalized = normalizeName(name);
      if (normalized != null && this.kits.containsKey(normalized)) {
         this.kit.setValue(normalized);
         this.arm();
         return true;
      } else {
         return false;
      }
   }

   public synchronized boolean deleteKit(String name) {
      String normalized = normalizeName(name);
      if (normalized != null && this.kits.remove(normalized) != null) {
         if (normalized.equals(this.kit.getValue())) {
            this.kit.setValue(this.kits.isEmpty() ? "default" : this.kits.keySet().iterator().next());
         }

         this.arm();
         return true;
      } else {
         return false;
      }
   }

   public synchronized Set<String> listKits() {
      return Collections.unmodifiableSet(new LinkedHashSet<>(this.kits.keySet()));
   }

   public String selectedKitName() {
      return this.kit.getValue();
   }

   private synchronized List<String> kitNames() {
      return this.kits.isEmpty() ? List.of("default") : List.copyOf(this.kits.keySet());
   }

   private synchronized Map<Integer, String> selectedKit() {
      Map<Integer, String> selected = this.kits.get(this.kit.getValue());
      return selected == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(selected));
   }

   private void saveFromSetting() {
      int saved = this.saveKit(this.kitName.getValue());
      if (saved == 0) {
         ChatUtil.send("{red}Enter a kit name and join a world with items in your inventory.");
      } else {
         Homovore2Client.config().save();
         ChatUtil.send("Saved kit {green}%s{reset} with {green}%d{reset} occupied slot(s).", this.selectedKitName(), saved);
      }
   }

   private void deleteFromSetting() {
      String selected = this.selectedKitName();
      if (!this.deleteKit(selected)) {
         ChatUtil.send("{red}No kit named %s is saved.", selected);
      } else {
         Homovore2Client.config().save();
         ChatUtil.send("Deleted kit {green}%s{reset}.", selected);
      }
   }

   private void arm() {
      this.firedThisContainer = false;
   }

   private ScreenHandler rekitMenu() {
      if (mc.player != null && mc.world != null) {
         if (mc.currentScreen instanceof HandledScreen<?> screen && !(screen instanceof InventoryScreen) && !isEnderChest(screen)) {
            ScreenHandler menu = mc.player.currentScreenHandler;
            return menu != null && menu.syncId != 0 && menu.slots.size() > 36 ? menu : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean isEnderChest(HandledScreen<?> screen) {
      Text title = screen.getTitle();
      return title.getContent() instanceof TranslatableTextContent translated && "container.enderchest".equals(translated.getKey())
         ? true
         : title.getString().equalsIgnoreCase("Ender Chest");
   }

   private InstantRekitModule.ClickPlan plan(ScreenHandler menu, Map<Integer, String> selected) {
      PlayerInventory inventory = mc.player.getInventory();
      int[] playerSlots = new int[36];
      Arrays.fill(playerSlots, -1);
      List<Integer> sources = new ArrayList<>();
      List<ItemStack> simulated = new ArrayList<>(menu.slots.size());

      for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
         Slot slot = (Slot)menu.slots.get(menuSlot);
         simulated.add(slot.getStack().copy());
         if (slot.inventory == inventory) {
            int inventorySlot = slot.getIndex();
            if (inventorySlot >= 0 && inventorySlot < playerSlots.length) {
               playerSlots[inventorySlot] = menuSlot;
            }
         } else if (slot.canTakeItems(mc.player)) {
            sources.add(menuSlot);
         }
      }

      List<ContainerClicks.PlannedClick> clicks = new ArrayList<>();

      for (Entry<Integer, String> entry : selected.entrySet()) {
         int inventorySlot = entry.getKey();
         if (inventorySlot >= 0 && inventorySlot < playerSlots.length) {
            int targetSlot = playerSlots[inventorySlot];
            Item wanted = item(entry.getValue());
            if (targetSlot >= 0 && wanted != null) {
               ItemStack target = simulated.get(targetSlot);
               if (!target.isOf(wanted)) {
                  int sourceSlot = bestSource(menu, sources, simulated, wanted, target, targetSlot, false);
                  if (sourceSlot >= 0) {
                     ItemStack source = simulated.get(sourceSlot);
                     if (inventorySlot < PlayerInventory.getHotbarSize()) {
                        clicks.add(new ContainerClicks.PlannedClick(sourceSlot, inventorySlot, SlotActionType.SWAP));
                     } else {
                        clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, SlotActionType.PICKUP));
                        clicks.add(new ContainerClicks.PlannedClick(targetSlot, 0, SlotActionType.PICKUP));
                        if (!target.isEmpty()) {
                           clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, SlotActionType.PICKUP));
                        }
                     }

                     simulated.set(sourceSlot, target.copy());
                     simulated.set(targetSlot, source.copy());
                  }
               }
            }
         }
      }

      if (this.topUpStacks.getValue()) {
         for (Entry<Integer, String> entry : selected.entrySet()) {
            int inventorySlot = entry.getKey();
            if (inventorySlot >= 0 && inventorySlot < playerSlots.length) {
               int targetSlot = playerSlots[inventorySlot];
               Item wanted = item(entry.getValue());
               if (targetSlot >= 0 && wanted != null) {
                  ItemStack target = simulated.get(targetSlot);
                  if (target.isOf(wanted) && target.isStackable()) {
                     int need = target.getMaxCount() - target.getCount();

                     while (need > 0) {
                        int sourceSlot = bestSource(menu, sources, simulated, wanted, target, targetSlot, true);
                        if (sourceSlot < 0) {
                           break;
                        }

                        ItemStack source = simulated.get(sourceSlot);
                        int moved = Math.min(need, source.getCount());
                        int remaining = source.getCount() - moved;
                        clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, SlotActionType.PICKUP));
                        clicks.add(new ContainerClicks.PlannedClick(targetSlot, 0, SlotActionType.PICKUP));
                        if (remaining > 0) {
                           clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, SlotActionType.PICKUP));
                        }

                        simulated.set(targetSlot, target.copyWithCount(target.getCount() + moved));
                        simulated.set(sourceSlot, remaining == 0 ? ItemStack.EMPTY : source.copyWithCount(remaining));
                        target = simulated.get(targetSlot);
                        need -= moved;
                     }
                  }
               }
            }
         }
      }

      return new InstantRekitModule.ClickPlan(menu.getRevision(), List.copyOf(clicks));
   }

   private static int bestSource(
      ScreenHandler menu, List<Integer> sources, List<ItemStack> simulated, Item wanted, ItemStack target, int targetSlot, boolean merging
   ) {
      int best = -1;
      int bestCount = 0;

      for (int sourceSlot : sources) {
         ItemStack source = simulated.get(sourceSlot);
         if (source.isOf(wanted)
            && source.getCount() > bestCount
            && menu.getSlot(targetSlot).canInsert(source)
            && (!merging || ItemStack.areItemsAndComponentsEqual(source, target))) {
            int moved = merging ? Math.min(source.getCount(), target.getMaxCount() - target.getCount()) : source.getCount();
            ItemStack leftBehind = merging ? (moved < source.getCount() ? source.copyWithCount(source.getCount() - moved) : ItemStack.EMPTY) : target;
            if (leftBehind.isEmpty() || menu.getSlot(sourceSlot).canInsert(leftBehind)) {
               best = sourceSlot;
               bestCount = source.getCount();
            }
         }
      }

      return best;
   }

   private static Item item(String id) {
      Identifier key = Identifier.tryParse(id);
      return key == null ? null : (Item)Registries.ITEM.getOptionalValue(key).orElse(null);
   }

   private static String normalizeName(String name) {
      if (name == null) {
         return null;
      }

      String normalized = name.trim().toLowerCase(Locale.ROOT);
      return normalized.isEmpty() ? null : normalized;
   }

   @Override
   public synchronized JsonElement toJson() {
      JsonObject object = super.toJson().getAsJsonObject();
      JsonObject storedKits = new JsonObject();

      for (Entry<String, Map<Integer, String>> named : this.kits.entrySet()) {
         JsonArray slots = new JsonArray();

         for (Entry<Integer, String> entry : named.getValue().entrySet()) {
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", entry.getKey());
            slot.addProperty("item", entry.getValue());
            slots.add(slot);
         }

         storedKits.add(named.getKey(), slots);
      }

      object.add("Kits", storedKits);
      object.addProperty("ActiveKit", this.kit.getValue());
      return object;
   }

   @Override
   public synchronized JsonObject toFunctionalJson() {
      JsonObject object = super.toFunctionalJson();
      object.remove("Kits");
      object.remove("ActiveKit");
      object.remove(this.kit.getName());
      object.remove(this.kitName.getName());
      return object;
   }

   @Override
   public synchronized void fromJson(JsonElement element) {
      JsonElement legacyKit = element != null && element.isJsonObject() ? element.getAsJsonObject().get("Kit") : null;
      boolean carriesKits = element == null || !element.isJsonObject() || element.getAsJsonObject().has("Kits") || legacyKit != null && legacyKit.isJsonArray();
      if (carriesKits) {
         this.kits.clear();
         this.kit.setValueNoEvent("default");
      }

      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         JsonElement stored = object.get("Kits");
         if (stored != null && stored.isJsonObject()) {
            for (Entry<String, JsonElement> named : stored.getAsJsonObject().entrySet()) {
               String name = normalizeName(named.getKey());
               if (name != null && named.getValue().isJsonArray()) {
                  Map<Integer, String> loaded = readKit(named.getValue().getAsJsonArray());
                  if (!loaded.isEmpty()) {
                     this.kits.put(name, Collections.unmodifiableMap(new LinkedHashMap<>(loaded)));
                  }
               }
            }
         }

         JsonElement legacy = object.get("Kit");
         JsonObject settings = object.deepCopy();
         if (legacy != null && legacy.isJsonArray()) {
            settings.remove("Kit");
            Map<Integer, String> loaded = readKit(legacy.getAsJsonArray());
            if (!loaded.isEmpty()) {
               this.kits.putIfAbsent("default", Collections.unmodifiableMap(new LinkedHashMap<>(loaded)));
            }
         }

         super.fromJson(settings);
         JsonElement sourceSelection = object.get("ActiveKit");
         if ((object.get("Kit") == null || !object.get("Kit").isJsonPrimitive()) && sourceSelection != null && sourceSelection.isJsonPrimitive()) {
            String selected = normalizeName(sourceSelection.getAsString());
            if (selected != null) {
               this.kit.setValue(selected);
            }
         }

         String selected = normalizeName(this.kit.getValue());
         if (this.kits.isEmpty()) {
            selected = "default";
         } else if (selected == null || !this.kits.containsKey(selected)) {
            selected = this.kits.containsKey("default") ? "default" : this.kits.keySet().iterator().next();
         }

         this.kit.setValue(selected);
         this.arm();
      } else {
         super.fromJson(element);
         this.arm();
      }
   }

   private static Map<Integer, String> readKit(JsonArray array) {
      Map<Integer, String> loaded = new LinkedHashMap<>();

      for (JsonElement element : array) {
         if (element.isJsonObject()) {
            JsonObject slot = element.getAsJsonObject();
            if (slot.has("slot") && slot.has("item")) {
               try {
                  int index = slot.get("slot").getAsInt();
                  String item = slot.get("item").getAsString();
                  if (index >= 0 && index < 36 && Identifier.tryParse(item) != null) {
                     loaded.put(index, item);
                  }
               } catch (RuntimeException var7) {
               }
            }
         }
      }

      return loaded;
   }

   private record ClickPlan(int stateId, List<ContainerClicks.PlannedClick> clicks) {
   }
}
