package dev.leonetic.mixin.client;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.TablistModule;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_310;
import net.minecraft.class_355;
import net.minecraft.class_634;
import net.minecraft.class_640;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_355.class)
public abstract class MixinPlayerTabOverlay {
   @Inject(method = "method_48213", at = @At("HEAD"), cancellable = true)
   private void homovore$filterPlayers(CallbackInfoReturnable<List<class_640>> cir) {
      TablistModule module = Homovore.moduleManager.getModuleByClass(TablistModule.class);
      if (module != null && module.isEnabled()) {
         class_634 conn = class_310.method_1551().method_1562();
         if (conn != null) {
            List<class_640> all = conn.method_45732().stream().toList();
            List<class_640> filtered = all.stream()
               .filter(module::shouldShow)
               .sorted(Comparator.comparing(p -> p.method_2966().name(), String.CASE_INSENSITIVE_ORDER))
               .limit(80L)
               .toList();
            cir.setReturnValue(filtered);
         }
      }
   }

   @Inject(method = "method_1918", at = @At("RETURN"), cancellable = true)
   private void homovore$colorName(class_640 info, CallbackInfoReturnable<class_2561> cir) {
      TablistModule module = Homovore.moduleManager.getModuleByClass(TablistModule.class);
      if (module != null && module.isEnabled()) {
         String name = info.method_2966().name();
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

            class_2561 original = (class_2561)cir.getReturnValue();
            cir.setReturnValue(original.method_27661().method_27696(class_2583.field_24360.method_36139(color.getRGB() & 16777215)));
         }
      }
   }
}
