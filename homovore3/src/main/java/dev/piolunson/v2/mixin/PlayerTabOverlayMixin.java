package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.ModuleManager;
import dev.piolunson.v2.feature.module.impl.TablistModule;
import dev.piolunson.v2.render.PlayerBadges;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerTabOverlayMixin {
   @Shadow
   @Final
   private static Comparator<PlayerListEntry> ENTRY_ORDERING;
   @Unique
   private static final long homovore$MAX_ROWS = 80L;

   @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
   private void homovore$badge(PlayerListEntry info, CallbackInfoReturnable<Text> cir) {
      Text name = (Text)cir.getReturnValue();
      PlayerBadges.Badge badge = PlayerBadges.get(info.getProfile().id(), info.getProfile().name());
      if (badge != null) {
         name = PlayerBadges.decorate(name, badge);
      }

      TablistModule tablist = homovore$tablist();
      if (tablist != null) {
         name = tablist.decorate(info, name);
      }

      if (name != cir.getReturnValue()) {
         cir.setReturnValue(name);
      }
   }

   @Inject(method = "collectPlayerEntries", at = @At("HEAD"), cancellable = true)
   private void homovore$filter(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
      TablistModule tablist = homovore$tablist();
      if (tablist != null) {
         ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
         if (connection != null) {
            List<PlayerListEntry> sorted = connection.getListedPlayerListEntries().stream().sorted(ENTRY_ORDERING).toList();
            List<PlayerListEntry> shown = tablist.filter(sorted);
            cir.setReturnValue(shown.size() > 80L ? shown.subList(0, 80) : shown);
         }
      }
   }

   private static TablistModule homovore$tablist() {
      ModuleManager modules = Homovore2Client.modules();
      if (modules == null) {
         return null;
      }

      TablistModule tablist = modules.get(TablistModule.class);
      return tablist != null && tablist.isEnabled() ? tablist : null;
   }
}
