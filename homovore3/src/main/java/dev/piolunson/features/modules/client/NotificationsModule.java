package dev.piolunson.features.modules.client;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.ClientEvent;
import dev.piolunson.event.impl.entity.TotemPopEvent;
import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.impl.network.DisconnectEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.hud.NotifierHudModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.TextUtil;
import dev.piolunson.util.player.ChatUtil;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class NotificationsModule extends Module {
   private static final String MODULE_FORMAT = "%s %s %s";
   private static final int INFO_COLOR = 16777045;
   public Setting<Boolean> moduleToggle = this.bool("Module Toggle", true);
   public Setting<Boolean> deaths = this.bool("Deaths", true);
   public Setting<Boolean> totemPops = this.bool("TotemPops", true);
   public Setting<Boolean> gear = this.bool("Gear", false);
   private final Set<UUID> lastTickAlive = ConcurrentHashMap.newKeySet();
   private final NotificationsModule.GearItem[] gearItems = new NotificationsModule.GearItem[]{
      new NotificationsModule.GearItem(Items.OBSIDIAN, "obsidian", this.gearThreshold("Obsidian", 128, 512)),
      new NotificationsModule.GearItem(Items.EXPERIENCE_BOTTLE, "XP bottles", this.gearThreshold("XP Bottles", 128, 512)),
      new NotificationsModule.GearItem(Items.END_CRYSTAL, "crystals", this.gearThreshold("Crystals", 128, 512)),
      new NotificationsModule.GearItem(Items.ENDER_PEARL, "pearls", this.gearThreshold("Pearls", 32, 256))
   };
   private final Set<Item> gearWarned = ConcurrentHashMap.newKeySet();

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
         if (mc.world != null && mc.player != null) {
            Set<UUID> currentlyAlive = new HashSet<>();

            for (PlayerEntity player : mc.world.getPlayers()) {
               UUID id = player.getUuid();
               if (player.isDead()) {
                  if (this.lastTickAlive.contains(id)) {
                     int pops = Homovore.playerInfoManager.getTotemPops(id);
                     String name = player.getGameProfile().name();
                     String unit = pops == 1 ? "totem" : "totems";
                     Text message = Text.empty()
                        .append(Text.literal(name + " has died after popping "))
                        .append(Text.literal(String.valueOf(pops)).withColor(16777045))
                        .append(Text.literal(" " + unit + "."));
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
         if (mc.player != null) {
            if (event.getEntity() instanceof PlayerEntity player) {
               UUID id = player.getUuid();
               int pops = Homovore.playerInfoManager.getTotemPops(id);
               String unit = pops == 1 ? "totem" : "totems";
               boolean self = player == mc.player;
               String subject = self ? "You have" : player.getGameProfile().name() + " has";
               Text body = Text.empty()
                  .append(Text.literal(subject + " popped "))
                  .append(Text.literal(String.valueOf(pops)).withColor(16777045))
                  .append(Text.literal(" " + unit + "!"));
               ChatUtil.sendPersistentPrefixed("pops:" + id, ChatUtil.getInfoComponent(), body);
               NotifierHudModule.push("pops:" + id, NotifierHudModule.Icon.INFO, body);
            }
         }
      }
   }

   @Subscribe
   public void onGearTick(PreTickEvent event) {
      if (this.moduleToggle.getValue() && this.gear.getValue()) {
         if (mc.player != null) {
            for (NotificationsModule.GearItem gi : this.gearItems) {
               int count = this.countItem(gi.item());
               int threshold = gi.threshold().getValue();
               if (count >= threshold) {
                  this.gearWarned.remove(gi.item());
               } else if (this.gearWarned.add(gi.item())) {
                  Text body = Text.empty()
                     .append(Text.literal("Low on " + gi.label() + ": "))
                     .append(Text.literal(String.valueOf(count)).withColor(16777045))
                     .append(Text.literal(" left"));
                  NotifierHudModule.push("gear:" + gi.label(), NotifierHudModule.Icon.INFO, body);
               }
            }
         }
      }
   }

   private int countItem(Item item) {
      int total = 0;
      PlayerInventory inv = mc.player.getInventory();

      for (int i = 0; i < inv.size(); i++) {
         ItemStack s = inv.getStack(i);
         if (s.isOf(item)) {
            total += s.getCount();
         }
      }

      return total;
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.lastTickAlive.clear();
      this.gearWarned.clear();
   }

   private record GearItem(Item item, String label, Setting<Integer> threshold) {
   }
}
