package dev.leonetic.features.modules.hud;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudModule;
import dev.leonetic.util.EnchantmentUtil;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_332;
import net.minecraft.class_3486;
import net.minecraft.class_5321;

public class ArmorHudModule extends HudModule {
   private static final int ICON = 16;
   private static final int STRIDE = 18;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final float TAG_SCALE = 0.8F;
   private static final class_1304[] SLOTS = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};
   private static final ArmorHudModule.ProtTag[] PROT_TAGS = new ArmorHudModule.ProtTag[]{
      new ArmorHudModule.ProtTag(class_1893.field_9111, "P"),
      new ArmorHudModule.ProtTag(class_1893.field_9107, "BP"),
      new ArmorHudModule.ProtTag(class_1893.field_9095, "FP"),
      new ArmorHudModule.ProtTag(class_1893.field_9096, "PP")
   };

   public ArmorHudModule() {
      super("Armor");
   }

   @Override
   public void render(Render2DEvent event) {
      int totalWidth = SLOTS.length * 18 - 2;
      int rightX = this.screenWidth() / 2 + 91;
      int x = rightX - totalWidth;
      int y = this.screenHeight() - 39 - 1 - 16;
      if (this.showingBubbles()) {
         y -= 10;
      }

      class_332 ctx = event.getContext();

      for (int i = 0; i < SLOTS.length; i++) {
         int sx = x + i * 18;
         class_1799 stack = mc.field_1724.method_6118(SLOTS[i]);
         if (!stack.method_7960()) {
            ctx.method_51427(stack, sx, y);
            ctx.method_51431(mc.field_1772, stack, sx, y);
            String tag = this.protectionTag(stack);
            if (tag != null) {
               ctx.method_51448().pushMatrix();
               ctx.method_51448().scale(0.8F, 0.8F);
               ctx.method_51433(mc.field_1772, tag, Math.round(sx / 0.8F), Math.round(y / 0.8F), -1, true);
               ctx.method_51448().popMatrix();
            }
         }
      }
   }

   private String protectionTag(class_1799 stack) {
      for (ArmorHudModule.ProtTag t : PROT_TAGS) {
         if (EnchantmentUtil.has(t.key(), stack)) {
            return t.label();
         }
      }

      return null;
   }

   private boolean showingBubbles() {
      return mc.field_1724.method_5777(class_3486.field_15517) || mc.field_1724.method_5669() < mc.field_1724.method_5748();
   }

   private record ProtTag(class_5321<class_1887> key, String label) {
   }
}
