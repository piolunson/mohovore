package dev.piolunson.v2.render;

import dev.piolunson.v2.Homovore2;
import dev.piolunson.v2.manager.PotionEffectManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class Nametag {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   public static final int PADDING_H = 1;
   public static final int PADDING_TOP = 1;
   public static final int ITEM_SIZE = 16;
   public static final int ITEM_GAP = 1;
   public static final int ROW_GAP = 1;
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{
      EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
   };
   private static final float POTION_SCALE = 1.35F;
   private static final int POTION_TEXT_GAP = 1;
   private static final int POTION_GAP = 2;
   private static final Identifier LOGO = Homovore2.id("textures/font/logo.png");
   private static final int LOGO_TEXTURE_SIZE = 128;
   private static final int LOGO_GAP = 2;
   private static final float WORLD_SCALE = 0.025F;
   private static final float MIN_SCALE = 0.667F;

   private Nametag() {
   }

   public static Vector3f up(Camera camera) {
      return camera.getRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
   }

   public static Nametag.Placement place(DrawContext graphics, Matrix4f viewProjection, Vec3d cameraPos, Vector3f up, double x, double y, double z) {
      Vector2f screen = project(graphics, viewProjection, cameraPos, x, y, z);
      if (screen == null) {
         return null;
      }

      Vector2f above = project(graphics, viewProjection, cameraPos, x + up.x * 0.025F, y + up.y * 0.025F, z + up.z * 0.025F);
      return above == null ? null : new Nametag.Placement(screen.x, screen.y, Math.max(Math.abs(screen.y - above.y), 0.667F));
   }

   public static Map<EquipmentSlot, ItemStack> equipment(LivingEntity entity) {
      Map<EquipmentSlot, ItemStack> stacks = new LinkedHashMap<>();

      for (EquipmentSlot slot : SLOTS) {
         ItemStack stack = entity.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            stacks.put(slot, stack);
         }
      }

      return stacks;
   }

   public static int itemsWidth(Collection<ItemStack> equipment) {
      return equipment.isEmpty() ? 0 : equipment.size() * 16 + (equipment.size() - 1) * 1;
   }

   public static int itemsHeight(Collection<ItemStack> equipment) {
      return equipment.isEmpty() ? 0 : 17;
   }

   public static void drawItems(DrawContext graphics, Collection<ItemStack> equipment, int x, int y) {
      for (ItemStack stack : equipment) {
         graphics.drawItem(stack, x, y);
         graphics.drawStackOverlay(mc.textRenderer, stack, x, y);
         x += 17;
      }
   }

   public static int potionSize() {
      return Math.max(1, Math.round(Render2D.textHeight() * 1.35F));
   }

   public static int potionsWidth(List<PotionEffectManager.Icon> potions) {
      return potionsWidth(potions, potionSize());
   }

   public static int potionsWidth(List<PotionEffectManager.Icon> potions, int size) {
      if (potions.isEmpty()) {
         return 0;
      }

      int width = 0;

      for (PotionEffectManager.Icon potion : potions) {
         width += size + 2;
         if (!potion.duration().isEmpty()) {
            width += 1 + Render2D.width(potion.duration());
         }
      }

      return width;
   }

   public static float drawPotions(DrawContext graphics, List<PotionEffectManager.Icon> potions, float x, float textY, int color, boolean shadow) {
      return drawPotions(graphics, potions, x, textY, potionSize(), color, shadow);
   }

   public static float drawPotions(DrawContext graphics, List<PotionEffectManager.Icon> potions, float x, float textY, int size, int color, boolean shadow) {
      float iconScale = size / 16.0F;
      float iconY = textY + (Render2D.textHeight() - size) / 2.0F;
      Matrix3x2fStack pose = graphics.getMatrices();

      for (PotionEffectManager.Icon potion : potions) {
         pose.pushMatrix();
         pose.translate(x, iconY);
         pose.scale(iconScale, iconScale);
         graphics.drawItem(potion.stack(), 0, 0);
         pose.popMatrix();
         x += size;
         if (!potion.duration().isEmpty()) {
            Render2D.text(graphics, potion.duration(), ++x, textY, color, shadow);
            x += Render2D.width(potion.duration());
         }

         x += 2.0F;
      }

      return x;
   }

   public static int logoWidth(boolean logo) {
      return logo ? Render2D.capHeight() + 2 : 0;
   }

   public static void drawLogo(DrawContext graphics, int x, int textY) {
      int size = Render2D.capHeight();
      Render2D.texture(graphics, LOGO, x, textY + Render2D.capTopOffset(), size, size, 128, 128, -1);
   }

   public static int partWidth(PlayerBadges.Part part) {
      return Render2D.canDisplay(part.text()) ? Render2D.width(part.text()) : mc.textRenderer.getWidth(part.text());
   }

   public static void drawPart(DrawContext graphics, PlayerBadges.Part part, int x, int y, boolean shadow) {
      if (Render2D.canDisplay(part.text())) {
         Render2D.text(graphics, part.text(), x, y, part.color(), shadow);
      } else {
         graphics.drawText(mc.textRenderer, part.text(), x, y, Render2D.fade(part.color()), shadow);
      }
   }

   private static Vector2f project(DrawContext graphics, Matrix4f viewProjection, Vec3d cameraPos, double x, double y, double z) {
      Vector4f clip = viewProjection.transform(new Vector4f((float)(x - cameraPos.x), (float)(y - cameraPos.y), (float)(z - cameraPos.z), 1.0F));
      if (clip.w <= 0.0F) {
         return null;
      }

      float ndcX = clip.x / clip.w;
      float ndcY = clip.y / clip.w;
      return new Vector2f((ndcX * 0.5F + 0.5F) * graphics.getScaledWindowWidth(), (0.5F - ndcY * 0.5F) * graphics.getScaledWindowHeight());
   }

   public record Placement(float x, float y, float scale) {
   }
}
