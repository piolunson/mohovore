package dev.leonetic.feature.module.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.ButtonSetting;
import dev.leonetic.feature.setting.ListSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.ChatUtil;
import dev.leonetic.util.inventory.ContainerClicks;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
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
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2588;
import net.minecraft.class_2960;
import net.minecraft.class_465;
import net.minecraft.class_490;
import net.minecraft.class_7923;

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
      class_1703 menu = this.rekitMenu();
      if (menu == null) {
         this.openContainerId = -1;
         this.firedThisContainer = false;
      } else {
         if (menu.field_7763 != this.openContainerId) {
            this.openContainerId = menu.field_7763;
            this.firedThisContainer = false;
         }

         if (!this.firedThisContainer && !this.pending.get()) {
            Map<Integer, String> selected = this.selectedKit();
            if (selected == null || selected.isEmpty()) {
               this.firedThisContainer = true;
               ChatUtil.send("{red}No kit named %s is saved. Use .savekit <name> or Save Current Kit.", this.kit.getValue());
            } else if (menu.method_34255().method_7960()) {
               InstantRekitModule.ClickPlan plan = this.plan(menu, selected);
               this.firedThisContainer = true;
               if (plan.clicks().isEmpty()) {
                  if (this.closeOnDone.getValue() && mc.field_1724 != null && mc.field_1724.field_7512.field_7763 == menu.field_7763) {
                     mc.field_1724.method_7346();
                  }
               } else {
                  this.pending.set(true);
                  int containerId = menu.field_7763;
                  Runnable close = this.closeOnDone.getValue() ? () -> {
                     if (mc.field_1724 != null && mc.field_1724.field_7512.field_7763 == containerId) {
                        mc.field_1724.method_7346();
                     }
                  } : null;
                  ContainerClicks.submitPlan(containerId, plan.stateId(), plan.clicks(), close).whenComplete((applied, failure) -> {
                     this.pending.set(false);
                     if (failure != null || !Boolean.TRUE.equals(applied)) {
                        ContainerClicks.dispatch(() -> {
                           if (mc.field_1724 != null && mc.field_1724.field_7512.field_7763 == containerId) {
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
         class_1799 stack = inventory.item(slot);
         if (!stack.method_7960()) {
            class_2960 id = class_7923.field_41178.method_10221(stack.method_7909());
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

   private class_1703 rekitMenu() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (mc.field_1755 instanceof class_465<?> screen && !(screen instanceof class_490) && !isEnderChest(screen)) {
            class_1703 menu = mc.field_1724.field_7512;
            return menu != null && menu.field_7763 != 0 && menu.field_7761.size() > 36 ? menu : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean isEnderChest(class_465<?> screen) {
      class_2561 title = screen.method_25440();
      return title.method_10851() instanceof class_2588 translated && "container.enderchest".equals(translated.method_11022())
         ? true
         : title.getString().equalsIgnoreCase("Ender Chest");
   }

   private InstantRekitModule.ClickPlan plan(class_1703 menu, Map<Integer, String> selected) {
      class_1661 inventory = mc.field_1724.method_31548();
      int[] playerSlots = new int[36];
      Arrays.fill(playerSlots, -1);
      List<Integer> sources = new ArrayList<>();
      List<class_1799> simulated = new ArrayList<>(menu.field_7761.size());

      for (int menuSlot = 0; menuSlot < menu.field_7761.size(); menuSlot++) {
         class_1735 slot = (class_1735)menu.field_7761.get(menuSlot);
         simulated.add(slot.method_7677().method_7972());
         if (slot.field_7871 == inventory) {
            int inventorySlot = slot.method_34266();
            if (inventorySlot >= 0 && inventorySlot < playerSlots.length) {
               playerSlots[inventorySlot] = menuSlot;
            }
         } else if (slot.method_7674(mc.field_1724)) {
            sources.add(menuSlot);
         }
      }

      List<ContainerClicks.PlannedClick> clicks = new ArrayList<>();

      for (Entry<Integer, String> entry : selected.entrySet()) {
         int inventorySlot = entry.getKey();
         if (inventorySlot >= 0 && inventorySlot < playerSlots.length) {
            int targetSlot = playerSlots[inventorySlot];
            class_1792 wanted = item(entry.getValue());
            if (targetSlot >= 0 && wanted != null) {
               class_1799 target = simulated.get(targetSlot);
               if (!target.method_31574(wanted)) {
                  int sourceSlot = bestSource(menu, sources, simulated, wanted, target, targetSlot, false);
                  if (sourceSlot >= 0) {
                     class_1799 source = simulated.get(sourceSlot);
                     if (inventorySlot < class_1661.method_7368()) {
                        clicks.add(new ContainerClicks.PlannedClick(sourceSlot, inventorySlot, class_1713.field_7791));
                     } else {
                        clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, class_1713.field_7790));
                        clicks.add(new ContainerClicks.PlannedClick(targetSlot, 0, class_1713.field_7790));
                        if (!target.method_7960()) {
                           clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, class_1713.field_7790));
                        }
                     }

                     simulated.set(sourceSlot, target.method_7972());
                     simulated.set(targetSlot, source.method_7972());
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
               class_1792 wanted = item(entry.getValue());
               if (targetSlot >= 0 && wanted != null) {
                  class_1799 target = simulated.get(targetSlot);
                  if (target.method_31574(wanted) && target.method_7946()) {
                     int need = target.method_7914() - target.method_7947();

                     while (need > 0) {
                        int sourceSlot = bestSource(menu, sources, simulated, wanted, target, targetSlot, true);
                        if (sourceSlot < 0) {
                           break;
                        }

                        class_1799 source = simulated.get(sourceSlot);
                        int moved = Math.min(need, source.method_7947());
                        int remaining = source.method_7947() - moved;
                        clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, class_1713.field_7790));
                        clicks.add(new ContainerClicks.PlannedClick(targetSlot, 0, class_1713.field_7790));
                        if (remaining > 0) {
                           clicks.add(new ContainerClicks.PlannedClick(sourceSlot, 0, class_1713.field_7790));
                        }

                        simulated.set(targetSlot, target.method_46651(target.method_7947() + moved));
                        simulated.set(sourceSlot, remaining == 0 ? class_1799.field_8037 : source.method_46651(remaining));
                        target = simulated.get(targetSlot);
                        need -= moved;
                     }
                  }
               }
            }
         }
      }

      return new InstantRekitModule.ClickPlan(menu.method_37421(), List.copyOf(clicks));
   }

   private static int bestSource(
      class_1703 menu, List<Integer> sources, List<class_1799> simulated, class_1792 wanted, class_1799 target, int targetSlot, boolean merging
   ) {
      int best = -1;
      int bestCount = 0;

      for (int sourceSlot : sources) {
         class_1799 source = simulated.get(sourceSlot);
         if (source.method_31574(wanted)
            && source.method_7947() > bestCount
            && menu.method_7611(targetSlot).method_7680(source)
            && (!merging || class_1799.method_31577(source, target))) {
            int moved = merging ? Math.min(source.method_7947(), target.method_7914() - target.method_7947()) : source.method_7947();
            class_1799 leftBehind = merging
               ? (moved < source.method_7947() ? source.method_46651(source.method_7947() - moved) : class_1799.field_8037)
               : target;
            if (leftBehind.method_7960() || menu.method_7611(sourceSlot).method_7680(leftBehind)) {
               best = sourceSlot;
               bestCount = source.method_7947();
            }
         }
      }

      return best;
   }

   private static class_1792 item(String id) {
      class_2960 key = class_2960.method_12829(id);
      return key == null ? null : (class_1792)class_7923.field_41178.method_17966(key).orElse(null);
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
                  if (index >= 0 && index < 36 && class_2960.method_12829(item) != null) {
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
