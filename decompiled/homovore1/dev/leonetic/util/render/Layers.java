package dev.leonetic.util.render;

import net.minecraft.class_12247;
import net.minecraft.class_1921;

public class Layers {
   private static final class_1921 GLOBAL_QUADS = class_1921.method_75940(
      "global_fill", class_12247.method_75927(Pipelines.GLOBAL_QUADS_PIPELINE).method_75938()
   );
   private static final class_1921 GLOBAL_LINES = class_1921.method_75940(
      "global_lines", class_12247.method_75927(Pipelines.GLOBAL_LINES_PIPELINE).method_75938()
   );

   public static class_1921 quads() {
      return GLOBAL_QUADS;
   }

   public static class_1921 lines() {
      return GLOBAL_LINES;
   }
}
