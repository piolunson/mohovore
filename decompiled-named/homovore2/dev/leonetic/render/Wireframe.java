package dev.leonetic.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

public final class Wireframe {
   private Wireframe() {
   }

   public static Wireframe.Capture capture(Entity entity, float partialTick) {
      if (entity == null) {
         return Wireframe.Capture.EMPTY;
      }

      double halfWidth = entity.getWidth() / 2.0;
      double height = entity.getHeight();
      Vec3d bottomFrontLeft = new Vec3d(-halfWidth, 0.0, -halfWidth);
      Vec3d bottomFrontRight = new Vec3d(halfWidth, 0.0, -halfWidth);
      Vec3d bottomBackLeft = new Vec3d(-halfWidth, 0.0, halfWidth);
      Vec3d bottomBackRight = new Vec3d(halfWidth, 0.0, halfWidth);
      Vec3d topFrontLeft = new Vec3d(-halfWidth, height, -halfWidth);
      Vec3d topFrontRight = new Vec3d(halfWidth, height, -halfWidth);
      Vec3d topBackLeft = new Vec3d(-halfWidth, height, halfWidth);
      Vec3d topBackRight = new Vec3d(halfWidth, height, halfWidth);
      List<Vec3d[]> faces = new ArrayList<>(6);
      faces.add(new Vec3d[]{bottomFrontLeft, bottomFrontRight, topFrontRight, topFrontLeft});
      faces.add(new Vec3d[]{bottomBackRight, bottomBackLeft, topBackLeft, topBackRight});
      faces.add(new Vec3d[]{bottomBackLeft, bottomFrontLeft, topFrontLeft, topBackLeft});
      faces.add(new Vec3d[]{bottomFrontRight, bottomBackRight, topBackRight, topFrontRight});
      faces.add(new Vec3d[]{topFrontLeft, topFrontRight, topBackRight, topBackLeft});
      faces.add(new Vec3d[]{bottomBackLeft, bottomBackRight, bottomFrontRight, bottomFrontLeft});
      return new Wireframe.Capture(faces, List.of());
   }

   public static void draw(List<Vec3d[]> quads, Vec3d pos, DrawStyle style, boolean throughWalls) {
      for (Vec3d[] quad : quads) {
         VisibilityConfigurable gizmo = GizmoDrawing.quad(quad[0].add(pos), quad[1].add(pos), quad[2].add(pos), quad[3].add(pos), style);
         if (throughWalls) {
            gizmo.ignoreOcclusion();
         }
      }
   }

   public record Capture(List<Vec3d[]> body, List<Vec3d[]> held) {
      public static final Wireframe.Capture EMPTY = new Wireframe.Capture(List.of(), List.of());

      public boolean isEmpty() {
         return this.body.isEmpty() && this.held.isEmpty();
      }
   }
}
