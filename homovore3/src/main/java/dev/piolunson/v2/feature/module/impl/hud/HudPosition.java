package dev.piolunson.v2.feature.module.impl.hud;

import java.util.Locale;

public enum HudPosition {
   TOP_LEFT(HudPosition.Side.LEFT, HudPosition.Row.TOP),
   TOP_RIGHT(HudPosition.Side.RIGHT, HudPosition.Row.TOP),
   CENTER_LEFT(HudPosition.Side.LEFT, HudPosition.Row.CENTER),
   CENTER_RIGHT(HudPosition.Side.RIGHT, HudPosition.Row.CENTER),
   BOTTOM_LEFT(HudPosition.Side.LEFT, HudPosition.Row.BOTTOM),
   BOTTOM_RIGHT(HudPosition.Side.RIGHT, HudPosition.Row.BOTTOM);

   private final HudPosition.Side side;
   private final HudPosition.Row row;

   HudPosition(HudPosition.Side side, HudPosition.Row row) {
      this.side = side;
      this.row = row;
   }

   public HudPosition.Side side() {
      return this.side;
   }

   public HudPosition.Row row() {
      return this.row;
   }

   public boolean isLeft() {
      return this.side == HudPosition.Side.LEFT;
   }

   public boolean isBottom() {
      return this.row == HudPosition.Row.BOTTOM;
   }

   public static HudPosition at(int x, int y, int width, int height) {
      HudPosition.Side side = x < width / 2 ? HudPosition.Side.LEFT : HudPosition.Side.RIGHT;
      HudPosition.Row row = y < height / 3 ? HudPosition.Row.TOP : (y < height * 2 / 3 ? HudPosition.Row.CENTER : HudPosition.Row.BOTTOM);

      for (HudPosition position : values()) {
         if (position.side == side && position.row == row) {
            return position;
         }
      }

      return BOTTOM_RIGHT;
   }

   public int[] zone(int width, int height) {
      int left = this.isLeft() ? 0 : width / 2;
      int right = this.isLeft() ? width / 2 : width;

      int top = switch (this.row) {
         case TOP -> 0;
         case CENTER -> height / 3;
         case BOTTOM -> height * 2 / 3;
      };

      int bottom = switch (this.row) {
         case TOP -> height / 3;
         case CENTER -> height * 2 / 3;
         case BOTTOM -> height;
      };
      return new int[]{left, top, right, bottom};
   }

   public String displayName() {
      StringBuilder out = new StringBuilder();

      for (String word : this.name().split("_")) {
         if (!out.isEmpty()) {
            out.append(' ');
         }

         out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
      }

      return out.toString();
   }

   public enum Row {
      TOP,
      CENTER,
      BOTTOM;
   }

   public enum Side {
      LEFT,
      RIGHT;
   }
}
