package dev.leonetic.mixin.client;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.TablistModule;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class MixinPlayerTabOverlay {
   @Inject(method = "collectPlayerEntries", at = @At("HEAD"), cancellable = true)
   private void homovore$filterPlayers(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
      TablistModule module = Homovore.moduleManager.getModuleByClass(TablistModule.class);
      if (module != null && module.isEnabled()) {
         ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
         if (conn != null) {
            List<PlayerListEntry> all = conn.getListedPlayerListEntries().stream().toList();
            List<PlayerListEntry> filtered = all.stream()
               .filter(module::shouldShow)
               .sorted(Comparator.comparing(p -> p.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
               .limit(80L)
               .toList();
            cir.setReturnValue(filtered);
         }
      }
   }

   @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
   private void homovore$colorName(PlayerListEntry info, CallbackInfoReturnable<Text> cir) {
      TablistModule module = Homovore.moduleManager.getModuleByClass(TablistModule.class);
      if (module != null && module.isEnabled()) {
         String name = info.getProfile().name();
         if (name != null) {
            Color color;
            if (Homovore.enemyManager.isEnemy(name)) {
               color = module.enemyColor.getValue();
            } else {
               if (!Homovore.friendManager.isFriend(name)) {
                  return;
               }

               color = module.friendColor.getValue();
            }

            Text original = (Text)cir.getReturnValue();
            cir.setReturnValue(original.copy().fillStyle(Style.EMPTY.withColor(color.getRGB() & 16777215)));
         }
      }
   }
}
