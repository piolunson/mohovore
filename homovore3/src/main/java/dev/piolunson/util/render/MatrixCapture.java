package dev.piolunson.util.render;

import dev.piolunson.util.traits.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class MatrixCapture {
   public static Matrix4f projection;
   public static Matrix4f view;

   public static float[] worldToScreen(double worldX, double worldY, double worldZ) {
      if (projection != null && view != null) {
         Vec3d camPos = Util.mc.gameRenderer.getCamera().getCameraPos();
         Matrix4f mvp = projection.mul(view, new Matrix4f());
         Vector4f point = new Vector4f((float)(worldX - camPos.getX()), (float)(worldY - camPos.getY()), (float)(worldZ - camPos.getZ()), 1.0F);
         mvp.transform(point);
         if (point.w <= 0.0F) {
            return null;
         } else {
            float ndcX = point.x / point.w;
            float ndcY = point.y / point.w;
            if (!(ndcX < -1.1F) && !(ndcX > 1.1F) && !(ndcY < -1.1F) && !(ndcY > 1.1F)) {
               int guiW = Util.mc.getWindow().getScaledWidth();
               int guiH = Util.mc.getWindow().getScaledHeight();
               return new float[]{(ndcX + 1.0F) / 2.0F * guiW, (1.0F - ndcY) / 2.0F * guiH};
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }
}
