package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.ClientEvent;
import dev.leonetic.event.impl.TotemPopEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.NotificationTray;
import dev.leonetic.util.ChatUtil;
import dev.leonetic.util.TextUtil;
import dev.leonetic.util.world.VisualRangeTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;

public class NotificationsModule extends Module {
   private static final int COUNT_COLOR = 16777045;
   public final Setting<NotificationsModule.Output> moduleToggles = this.mode("Module Toggles", NotificationsModule.Output.CHAT)
      .describe("Where a module being turned on or off is reported.");
   public final Setting<NotificationsModule.Output> visualRange = this.mode("Visual Range", NotificationsModule.Output.BOTH)
      .describe("Where a player coming into range is reported.");
   public final Setting<Boolean> ignoreFriends = this.bool("Ignore Friends", false)
      .describe("Keeps a friend coming into range from being reported.")
      .visibleWhen(() -> this.visualRange.getValue() != NotificationsModule.Output.OFF);
   public final Setting<Boolean> gearedOnly = this.bool("Geared Only", true)
      .describe("Only reports players in a full set of diamond or netherite armour.")
      .visibleWhen(() -> this.visualRange.getValue() != NotificationsModule.Output.OFF);
   public final Setting<NotificationsModule.Output> totemPops = this.mode("Totem Pops", NotificationsModule.Output.BOTH)
      .describe("Where a totem popping is reported, yours and everyone else's.");
   public final Setting<Integer> duration = this.integer("Duration", 3, 1, 15).describe("Seconds a tray notice stays on screen.").visibleWhen(this::usesTray);
   private final VisualRangeTracker tracker = new VisualRangeTracker();

   public NotificationsModule() {
      super("Notifications", Category.CLIENT, "Reports client and combat events as they happen.");
   }

   @Override
   public void onEnable() {
      this.tracker.forget();
   }

   @Override
   public void onDisable() {
      this.tracker.forget();
      NotificationTray.clear();
      if (this.moduleToggles.getValue().chat()) {
         ChatUtil.sendPersistent("module:" + this.getName(), TextUtil.text("%s {red}disabled", this.getDisplayName()));
      }
   }

   @Subscribe
   public void onClientEvent(ClientEvent event) {
      if (this.moduleToggles.getValue() != NotificationsModule.Output.OFF && event.getType() == ClientEvent.Type.TOGGLE_MODULE) {
         Module module = event.getModule();
         if (!(module instanceof ClickGuiModule)) {
            this.notifyToggle(module, module.isEnabled());
         }
      }
   }

   private void notifyToggle(Module module, boolean enabled) {
      String name = module.getDisplayName();
      String state = enabled ? "enabled" : "disabled";
      this.notify(
         this.moduleToggles.getValue(),
         "module:" + module.getName(),
         enabled ? NotificationTray.Icon.INFO : NotificationTray.Icon.WARNING,
         TextUtil.text("%s %s%s", name, enabled ? "{green}" : "{red}", state),
         name + " " + state
      );
   }

   @Subscribe
   public void onTotemPop(TotemPopEvent event) {
      if (this.totemPops.getValue() != NotificationsModule.Output.OFF) {
         int pops = event.getPops();
         String unit = pops == 1 ? "totem" : "totems";
         boolean self = event.isSelf();
         String subject = self ? "You have" : event.getPlayer().getName().getString() + " has";
         Text body = Text.empty()
            .append(Text.literal(subject + " popped "))
            .append(Text.literal(String.valueOf(pops)).withColor(16777045))
            .append(Text.literal(" " + unit + "!"));
         this.notify(
            this.totemPops.getValue(),
            "pops:" + event.getPlayer().getUuid(),
            self ? NotificationTray.Icon.DANGER : NotificationTray.Icon.INFO,
            body,
            subject + " popped " + pops + " " + unit + "!"
         );
      }
   }

   @Override
   public void onTick() {
      if (this.visualRange.getValue() == NotificationsModule.Output.OFF) {
         if (!this.tracker.isEmpty()) {
            this.tracker.forget();
         }
      } else {
         this.tracker
            .tick(this.gearedOnly.getValue(), player -> !this.ignoreFriends.getValue() || !Homovore2Client.friends().isFriend(player), this::notifyVisualRange);
      }
   }

   private void notifyVisualRange(AbstractClientPlayerEntity player) {
      String name = player.getName().getString();
      int distance = Math.round(mc.player.distanceTo(player));
      Text body = Text.empty()
         .append(Text.literal(name + " entered visual range ("))
         .append(Text.literal(distance + "m").withColor(16777045))
         .append(Text.literal(")"));
      this.notify(
         this.visualRange.getValue(), "range:" + player.getUuid(), NotificationTray.Icon.WARNING, body, name + " entered visual range (" + distance + "m)"
      );
   }

   private void notify(NotificationsModule.Output where, String key, NotificationTray.Icon icon, Text chat, String plain) {
      if (where.chat()) {
         ChatUtil.sendPersistent(key, chat);
      }

      if (where.tray()) {
         NotificationTray.push(key, icon, plain, this.duration.getValue().intValue() * 1000L);
      }
   }

   private boolean usesTray() {
      return this.moduleToggles.getValue().tray() || this.visualRange.getValue().tray() || this.totemPops.getValue().tray();
   }

   @Override
   public void onRender2D(DrawContext graphics, float partialTick) {
      if (this.usesTray()) {
         NotificationTray.render(graphics);
      }
   }

   @Override
   public boolean isPersonal() {
      return true;
   }

   public enum Output {
      OFF,
      CHAT,
      TRAY,
      BOTH;

      private boolean chat() {
         return this == CHAT || this == BOTH;
      }

      private boolean tray() {
         return this == TRAY || this == BOTH;
      }
   }
}
