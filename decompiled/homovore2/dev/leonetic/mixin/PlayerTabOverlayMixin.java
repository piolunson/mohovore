package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.module.impl.TablistModule;
import dev.leonetic.render.PlayerBadges;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_355;
import net.minecraft.class_634;
import net.minecraft.class_640;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_355.class)
public class PlayerTabOverlayMixin {
   @Shadow
   @Final
   private static Comparator<class_640> field_2156;
   @Unique
   private static final long homovore$MAX_ROWS = 80L;

   @Inject(method = "method_1918", at = @At("RETURN"), cancellable = true)
   private void homovore$badge(class_640 info, CallbackInfoReturnable<class_2561> cir) {
      class_2561 name = (class_2561)cir.getReturnValue();
      PlayerBadges.Badge badge = PlayerBadges.get(info.method_2966().id(), info.method_2966().name());
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

   @Inject(method = "method_48213", at = @At("HEAD"), cancellable = true)
   private void homovore$filter(CallbackInfoReturnable<List<class_640>> cir) {
      TablistModule tablist = homovore$tablist();
      if (tablist != null) {
         class_634 connection = class_310.method_1551().method_1562();
         if (connection != null) {
            List<class_640> sorted = connection.method_45732().stream().sorted(field_2156).toList();
            List<class_640> shown = tablist.filter(sorted);
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
