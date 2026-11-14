package dev.leonetic.mixin;

import dev.leonetic.Homovore2;
import dev.leonetic.util.ChatUtil;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_442;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_442.class)
public class TitleScreenMixin {
   private static final int MARGIN = 4;
   private static final int GAP = 3;
   private static final int COLOR = -1;

   @Inject(method = "method_25394", at = @At("TAIL"))
   private void homovore$signature(class_332 graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      class_310 mc = class_310.method_1551();
      class_2561 logo = ChatUtil.clientName();
      graphics.method_27535(mc.field_1772, logo, 4, 4, -1);
      graphics.method_27535(mc.field_1772, class_2561.method_43470(Homovore2.version()), 4 + mc.field_1772.method_27525(logo) + 3, 4, -1);
   }
}
