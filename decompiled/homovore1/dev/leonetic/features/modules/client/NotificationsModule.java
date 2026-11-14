package dev.leonetic.features.modules.client;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.ClientEvent;
import dev.leonetic.event.impl.entity.TotemPopEvent;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.hud.NotifierHudModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.TextUtil;
import dev.leonetic.util.player.ChatUtil;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;

public class NotificationsModule extends Module {
   private static final String MODULE_FORMAT = "%s %s %s";
   private static final int INFO_COLOR = 16777045;
   public Setting<Boolean> moduleToggle = this.bool("Module Toggle", true);
   public Setting<Boolean> deaths = this.bool("Deaths", true);
   public Setting<Boolean> totemPops = this.bool("TotemPops", true);
   public Setting<Boolean> gear = this.bool("Gear", false);
   private final Set<UUID> lastTickAlive = ConcurrentHashMap.newKeySet();
   private final NotificationsModule.GearItem[] gearItems = new NotificationsModule.GearItem[]{
      new NotificationsModule.GearItem(class_1802.field_8281, "obsidian", this.gearThreshold("Obsidian", 128, 512)),
      new NotificationsModule.GearItem(class_1802.field_8287, "XP bottles", this.gearThreshold("XP Bottles", 128, 512)),
      new NotificationsModule.GearItem(class_1802.field_8301, "crystals", this.gearThreshold("Crystals", 128, 512)),
      new NotificationsModule.GearItem(class_1802.field_8634, "pearls", this.gearThreshold("Pearls", 32, 256))
   };
   private final Set<class_1792> gearWarned = ConcurrentHashMap.newKeySet();

   private Setting<Integer> gearThreshold(String label, int def, int max) {
      Setting<Integer> s = this.num(label + " Threshold", def, 0, max);
      s.setVisibility(v -> this.gear.getValue());
      return s;
   }

   public NotificationsModule() {
      super("Notifications", "Displays notifications for various client events", Module.Category.CLIENT);
   }

   @Subscribe
   public void onClient(ClientEvent event) {
      if (this.moduleToggle.getValue() && event.getType() == ClientEvent.Type.TOGGLE_MODULE && !(event.getFeature() instanceof ClickGuiModule)) {
         boolean moduleState = event.getFeature().isEnabled();
         String name = event.getFeature().getName();
         ChatUtil.sendPersistent("module:" + name, TextUtil.text("%s %s %s", name, moduleState ? "{green}" : "{red}", moduleState ? "enabled" : "disabled"));
      }
   }

   @Subscribe(priority = 100)
   public void onPreTick(PreTickEvent event) {
      if (this.moduleToggle.getValue() && this.deaths.getValue()) {
         if (mc.field_1687 != null && mc.field_1724 != null) {
            Set<UUID> currentlyAlive = new HashSet<>();

            for (class_1657 player : mc.field_1687.method_18456()) {
               UUID id = player.method_5667();
               if (player.method_29504()) {
                  if (this.lastTickAlive.contains(id)) {
                     int pops = Homovore.playerInfoManager.getTotemPops(id);
                     String name = player.method_7334().name();
                     String unit = pops == 1 ? "totem" : "totems";
                     class_2561 message = class_2561.method_43473()
                        .method_10852(class_2561.method_43470(name + " has died after popping "))
                        .method_10852(class_2561.method_43470(String.valueOf(pops)).method_54663(16777045))
                        .method_10852(class_2561.method_43470(" " + unit + "."));
                     ChatUtil.sendPrefixed(ChatUtil.getSkullComponent(), message);
                  }
               } else {
                  currentlyAlive.add(id);
               }
            }

            this.lastTickAlive.clear();
            this.lastTickAlive.addAll(currentlyAlive);
         }
      }
   }

   @Subscribe(priority = -100)
   public void onTotemPop(TotemPopEvent event) {
      if (this.moduleToggle.getValue() && this.totemPops.getValue()) {
         if (mc.field_1724 != null) {
            if (event.getEntity() instanceof class_1657 player) {
               UUID id = player.method_5667();
               int pops = Homovore.playerInfoManager.getTotemPops(id);
               String unit = pops == 1 ? "totem" : "totems";
               boolean self = player == mc.field_1724;
               String subject = self ? "You have" : player.method_7334().name() + " has";
               class_2561 body = class_2561.method_43473()
                  .method_10852(class_2561.method_43470(subject + " popped "))
                  .method_10852(class_2561.method_43470(String.valueOf(pops)).method_54663(16777045))
                  .method_10852(class_2561.method_43470(" " + unit + "!"));
               ChatUtil.sendPersistentPrefixed("pops:" + id, ChatUtil.getInfoComponent(), body);
               NotifierHudModule.push("pops:" + id, NotifierHudModule.Icon.INFO, body);
            }
         }
      }
   }

   @Subscribe
   public void onGearTick(PreTickEvent event) {
      if (this.moduleToggle.getValue() && this.gear.getValue()) {
         if (mc.field_1724 != null) {
            for (NotificationsModule.GearItem gi : this.gearItems) {
               int count = this.countItem(gi.item());
               int threshold = gi.threshold().getValue();
               if (count >= threshold) {
                  this.gearWarned.remove(gi.item());
               } else if (this.gearWarned.add(gi.item())) {
                  class_2561 body = class_2561.method_43473()
                     .method_10852(class_2561.method_43470("Low on " + gi.label() + ": "))
                     .method_10852(class_2561.method_43470(String.valueOf(count)).method_54663(16777045))
                     .method_10852(class_2561.method_43470(" left"));
                  NotifierHudModule.push("gear:" + gi.label(), NotifierHudModule.Icon.INFO, body);
               }
            }
         }
      }
   }

   private int countItem(class_1792 item) {
      int total = 0;
      class_1661 inv = mc.field_1724.method_31548();

      for (int i = 0; i < inv.method_5439(); i++) {
         class_1799 s = inv.method_5438(i);
         if (s.method_31574(item)) {
            total += s.method_7947();
         }
      }

      return total;
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.lastTickAlive.clear();
      this.gearWarned.clear();
   }

   private record GearItem(class_1792 item, String label, Setting<Integer> threshold) {
   }
}
