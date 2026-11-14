package dev.leonetic.features.modules.client;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_408;

public abstract class HudModule implements Util {
   private static final int CHAT_INPUT_HEIGHT = 14;
   protected static final int SCREEN_MARGIN = 2;
   private final String name;

   public HudModule(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public abstract void render(Render2DEvent var1);

   protected int screenWidth() {
      return mc.method_22683().method_4486();
   }

   protected int screenHeight() {
      return mc.method_22683().method_4502();
   }

   protected int chatOffset() {
      return mc.field_1755 instanceof class_408 ? 14 : 0;
   }

   protected int bottomAnchor() {
      return this.screenHeight() - this.chatOffset();
   }

   protected int lineX(HudPosition pos, int lineWidth) {
      return pos.isLeft() ? 2 : this.screenWidth() - 2 - lineWidth;
   }

   protected int blockTop(HudPosition pos, int ownLines, int linesBelow, int gap) {
      return pos.isBottom() ? this.bottomAnchor() - 2 - (linesBelow + ownLines) * 9 - gap : this.screenHeight() / 2;
   }
}
