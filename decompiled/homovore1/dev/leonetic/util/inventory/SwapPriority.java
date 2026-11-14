package dev.leonetic.util.inventory;

public final class SwapPriority {
   public static final int OFFHAND = 500;
   public static final int PLACEMENT = 400;
   public static final int CRYSTAL = 350;
   public static final int ANCHOR = 300;
   public static final int WEAPON = 200;
   public static final int ESCAPE = 175;
   public static final int USER_ACTION = 150;
   public static final int MINING = 50;
   public static final int UTILITY = 0;

   private SwapPriority() {
      throw new AssertionError();
   }
}
