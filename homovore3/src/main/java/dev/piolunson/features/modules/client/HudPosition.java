package dev.piolunson.features.modules.client;

public enum HudPosition {
   BOTTOM_RIGHT,
   BOTTOM_LEFT,
   CENTER_RIGHT,
   CENTER_LEFT;

   public boolean isLeft() {
      return (Object)this == BOTTOM_LEFT || (Object)this == CENTER_LEFT;
   }

   public boolean isBottom() {
      return (Object)this == BOTTOM_LEFT || (Object)this == BOTTOM_RIGHT;
   }
}
