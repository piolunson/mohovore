package dev.leonetic.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_12178;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_1297;
import net.minecraft.class_243;

public final class Wireframe {
   private Wireframe() {
   }

   public static Wireframe.Capture capture(class_1297 entity, float partialTick) {
      if (entity == null) {
         return Wireframe.Capture.EMPTY;
      }

      double halfWidth = entity.method_17681() / 2.0;
      double height = entity.method_17682();
      class_243 bottomFrontLeft = new class_243(-halfWidth, 0.0, -halfWidth);
      class_243 bottomFrontRight = new class_243(halfWidth, 0.0, -halfWidth);
      class_243 bottomBackLeft = new class_243(-halfWidth, 0.0, halfWidth);
      class_243 bottomBackRight = new class_243(halfWidth, 0.0, halfWidth);
      class_243 topFrontLeft = new class_243(-halfWidth, height, -halfWidth);
      class_243 topFrontRight = new class_243(halfWidth, height, -halfWidth);
      class_243 topBackLeft = new class_243(-halfWidth, height, halfWidth);
      class_243 topBackRight = new class_243(halfWidth, height, halfWidth);
      List<class_243[]> faces = new ArrayList<>(6);
      faces.add(new class_243[]{bottomFrontLeft, bottomFrontRight, topFrontRight, topFrontLeft});
      faces.add(new class_243[]{bottomBackRight, bottomBackLeft, topBackLeft, topBackRight});
      faces.add(new class_243[]{bottomBackLeft, bottomFrontLeft, topFrontLeft, topBackLeft});
      faces.add(new class_243[]{bottomFrontRight, bottomBackRight, topBackRight, topFrontRight});
      faces.add(new class_243[]{topFrontLeft, topFrontRight, topBackRight, topBackLeft});
      faces.add(new class_243[]{bottomBackLeft, bottomBackRight, bottomFrontRight, bottomFrontLeft});
      return new Wireframe.Capture(faces, List.of());
   }

   public static void draw(List<class_243[]> quads, class_243 pos, class_12179 style, boolean throughWalls) {
      for (class_243[] quad : quads) {
         class_12178 gizmo = class_12180.method_75547(
            quad[0].method_1019(pos), quad[1].method_1019(pos), quad[2].method_1019(pos), quad[3].method_1019(pos), style
         );
         if (throughWalls) {
            gizmo.method_75533();
         }
      }
   }

   public record Capture(List<class_243[]> body, List<class_243[]> held) {
      public static final Wireframe.Capture EMPTY = new Wireframe.Capture(List.of(), List.of());

      public boolean isEmpty() {
         return this.body.isEmpty() && this.held.isEmpty();
      }
   }
}
