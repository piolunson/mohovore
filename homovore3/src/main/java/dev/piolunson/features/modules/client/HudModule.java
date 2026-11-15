package dev.piolunson.features.modules.client;

import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.util.traits.Util;
import net.minecraft.client.gui.screen.ChatScreen;

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
      return mc.getWindow().getScaledWidth();
   }

   protected int screenHeight() {
      return mc.getWindow().getScaledHeight();
   }

   protected int chatOffset() {
      return mc.currentScreen instanceof ChatScreen ? 14 : 0;
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
