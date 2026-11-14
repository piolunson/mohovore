package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Render2D;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_332;
import net.minecraft.class_5321;
import net.minecraft.class_6880;

public final class ArmorElement extends AnchoredElement {
   private static final int ICON = 16;
   private static final int STRIDE = 18;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final float TAG_SCALE = 0.8F;
   private static final class_1304[] SLOTS = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};
   private static final ArmorElement.ProtectionTag[] TAGS = new ArmorElement.ProtectionTag[]{
      new ArmorElement.ProtectionTag(class_1893.field_9111, "P"),
      new ArmorElement.ProtectionTag(class_1893.field_9107, "BP"),
      new ArmorElement.ProtectionTag(class_1893.field_9095, "FP"),
      new ArmorElement.ProtectionTag(class_1893.field_9096, "PP")
   };
   public final Setting<Boolean> protectionTags = this.register(new Setting<>("Protection", true)).describe("Tags each piece with the protection it carries.");

   public ArmorElement() {
      super("Armor", true);
   }

   @Override
   public void render(class_332 graphics, HudModule hud) {
      int width = SLOTS.length * 18 - 2;
      int left = graphics.method_51421() / 2 + 91 - width;
      int y = graphics.method_51443() - 39 - 1 - 16;
      if (showingBubbles()) {
         y -= 10;
      }

      for (int i = 0; i < SLOTS.length; i++) {
         class_1799 stack = mc.field_1724.method_6118(SLOTS[i]);
         if (!stack.method_7960()) {
            int x = left + i * 18;
            graphics.method_51427(stack, x, y);
            graphics.method_51431(mc.field_1772, stack, x, y);
            if (this.protectionTags.getValue()) {
               String tag = protectionTag(stack);
               if (tag != null) {
                  graphics.method_51448().pushMatrix();
                  graphics.method_51448().scale(0.8F, 0.8F);
                  Render2D.text(graphics, tag, Math.round(x / 0.8F), Math.round(y / 0.8F), -1, true);
                  graphics.method_51448().popMatrix();
               }
            }
         }
      }
   }

   private static String protectionTag(class_1799 stack) {
      for (ArmorElement.ProtectionTag tag : TAGS) {
         for (Entry<class_6880<class_1887>> entry : stack.method_58657().method_57539()) {
            if (((class_6880)entry.getKey()).method_40225(tag.key())) {
               return tag.label();
            }
         }
      }

      return null;
   }

   private record ProtectionTag(class_5321<class_1887> key, String label) {
   }
}
