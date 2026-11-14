package dev.leonetic.util.render;

import dev.leonetic.util.traits.Util;
import net.minecraft.class_243;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class MatrixCapture {
   public static Matrix4f projection;
   public static Matrix4f view;

   public static float[] worldToScreen(double worldX, double worldY, double worldZ) {
      if (projection != null && view != null) {
         class_243 camPos = Util.mc.field_1773.method_19418().method_71156();
         Matrix4f mvp = projection.mul(view, new Matrix4f());
         Vector4f point = new Vector4f(
            (float)(worldX - camPos.method_10216()), (float)(worldY - camPos.method_10214()), (float)(worldZ - camPos.method_10215()), 1.0F
         );
         mvp.transform(point);
         if (point.w <= 0.0F) {
            return null;
         } else {
            float ndcX = point.x / point.w;
            float ndcY = point.y / point.w;
            if (!(ndcX < -1.1F) && !(ndcX > 1.1F) && !(ndcY < -1.1F) && !(ndcY > 1.1F)) {
               int guiW = Util.mc.method_22683().method_4486();
               int guiH = Util.mc.method_22683().method_4502();
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
