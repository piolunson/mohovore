package dev.leonetic.util.world;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Objects;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_1922;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_259;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_3610;
import net.minecraft.class_3726;
import net.minecraft.class_5134;
import net.minecraft.class_6880;
import net.minecraft.class_2338.class_2339;

public final class ExplosionMath {
   public static final double CRYSTAL_POWER = 6.0;
   public static final double EFFECT_RADIUS = 12.0;
   public static final double NO_POP_MARGIN = 1.0;
   private static final float WEAKEST_RAY = 4.2F;
   private static final double RAY_STEP = 0.3F;
   private static final float RAY_DECAY = 0.22500001F;
   private static final double WEAKEST_RAY_REACH = 5.6F;
   private static final class_1304[] ARMOR_SLOTS = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};

   private ExplosionMath() {
      throw new AssertionError();
   }

   public static double damage(double distanceSquared, double exposure, double armor, double toughness, double resistance, int protection, int difficulty) {
      return damage(6.0, distanceSquared, exposure, armor, toughness, resistance, protection, difficulty);
   }

   public static double damage(
      double power, double distanceSquared, double exposure, double armor, double toughness, double resistance, int protection, int difficulty
   ) {
      double effectRadius = power * 2.0;
      if (!(power <= 0.0) && !(distanceSquared >= effectRadius * effectRadius)) {
         double impact = (1.0 - Math.sqrt(distanceSquared) / effectRadius) * exposure;
         if (impact <= 0.0) {
            return 0.0;
         }

         double raw = (impact * impact + impact) * (7.0 * power) + 1.0;

         raw = switch (difficulty) {
            case 0 -> 0.0;
            case 1 -> Math.min(raw * 0.5 + 1.0, raw);
            default -> raw;
            case 3 -> raw * 1.5;
         };
         double divisor = 2.0 + toughness * 0.25;
         double blocked = clamp(armor - raw / divisor, armor * 0.2, 20.0);
         raw *= 1.0 - blocked / 25.0;
         raw *= clamp(resistance, 0.0, 1.0);
         raw *= 1.0 - clamp(protection, 0.0, 20.0) / 25.0;
         return Math.max(0.0, raw);
      } else {
         return 0.0;
      }
   }

   public static boolean destroysBlock(class_1937 level, class_243 source, class_2338 target) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(target, "target");
      double dx = target.method_10263() + 0.5 - source.field_1352;
      double dy = target.method_10264() + 0.5 - source.field_1351;
      double dz = target.method_10260() + 0.5 - source.field_1350;
      double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (length > 5.6F) {
         return false;
      }

      if (length < 1.0E-7) {
         return true;
      }

      dx /= length;
      dy /= length;
      dz /= length;
      double x = source.field_1352;
      double y = source.field_1351;
      double z = source.field_1350;
      float strength = 4.2F;
      class_2339 cursor = new class_2339();

      while (strength > 0.0F) {
         cursor.method_10103(class_3532.method_15357(x), class_3532.method_15357(y), class_3532.method_15357(z));
         if (!level.method_24794(cursor)) {
            return false;
         }

         class_2680 state = level.method_8320(cursor);
         class_3610 fluid = level.method_8316(cursor);
         if (!state.method_26215() || !fluid.method_15769()) {
            float resistance = Math.max(state.method_26204().method_9520(), fluid.method_15760());
            strength -= (resistance + 0.3F) * 0.3F;
         }

         if (strength > 0.0F && cursor.equals(target)) {
            return true;
         }

         x += dx * 0.3F;
         y += dy * 0.3F;
         z += dz * 0.3F;
         strength -= 0.22500001F;
      }

      return false;
   }

   public static boolean safeForSelf(double selfDamage, double maxSelfDamage, double health, boolean invulnerable) {
      return invulnerable || selfDamage <= maxSelfDamage && selfDamage + 1.0 < health;
   }

   public static double selfDamage(class_1937 level, class_1657 player, class_243 source) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(player, "player");
      Objects.requireNonNull(source, "source");
      return player.method_31549().field_7480
         ? 0.0
         : damage(
            player.method_5707(source),
            exposure(level, source, player),
            player.method_6096(),
            player.method_45325(class_5134.field_23725),
            resistanceFactor(player),
            protectionPoints(player),
            level.method_8407().method_5461()
         );
   }

   public static float resistanceFactor(class_1309 entity) {
      class_1293 resistance = Objects.requireNonNull(entity, "entity").method_6112(class_1294.field_5907);
      return resistance == null ? 1.0F : Math.max(0.0F, 1.0F - 0.2F * (resistance.method_5578() + 1));
   }

   public static int protectionPoints(class_1657 player) {
      int points = 0;

      for (class_1304 slot : ARMOR_SLOTS) {
         class_1799 stack = Objects.requireNonNull(player, "player").method_6118(slot);
         if (!stack.method_7960()) {
            for (Entry<class_6880<class_1887>> enchantment : stack.method_58657().method_57539()) {
               int level = enchantment.getIntValue();
               if (((class_6880)enchantment.getKey()).method_40225(class_1893.field_9111)) {
                  points += level;
               } else if (((class_6880)enchantment.getKey()).method_40225(class_1893.field_9107)) {
                  points += level * 2;
               }
            }
         }
      }

      return Math.min(points, 20);
   }

   public static float exposure(class_1937 level, class_243 source, class_1297 entity) {
      return exposure(level, source, entity, null, null);
   }

   public static float exposureIgnoring(class_1937 level, class_243 source, class_1297 entity, class_2338 ignored) {
      return exposure(level, source, entity, ignored, null);
   }

   private static float exposure(class_1937 level, class_243 source, class_1297 entity, class_2338 ignored, class_2338 phantom) {
      class_238 box = Objects.requireNonNull(entity, "entity").method_5829();
      double stepX = 1.0 / (box.method_17939() * 2.0 + 1.0);
      double stepY = 1.0 / (box.method_17940() * 2.0 + 1.0);
      double stepZ = 1.0 / (box.method_17941() * 2.0 + 1.0);
      if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
         class_3726 collision = class_3726.method_16195(entity);
         ExplosionMath.ExposureRayContext ray = new ExplosionMath.ExposureRayContext(level, source, collision, ignored, phantom);
         double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) * 0.5;
         double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) * 0.5;
         int visible = 0;
         int total = 0;

         for (double fx = 0.0; fx <= 1.0; fx += stepX) {
            for (double fy = 0.0; fy <= 1.0; fy += stepY) {
               for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                  class_243 point = new class_243(
                     class_3532.method_16436(fx, box.field_1323, box.field_1320) + offsetX,
                     class_3532.method_16436(fy, box.field_1322, box.field_1325),
                     class_3532.method_16436(fz, box.field_1321, box.field_1324) + offsetZ
                  );
                  ray.start = point;
                  if (!(Boolean)class_1922.method_17744(point, source, ray, ExplosionMath::clipExposureBlock, ExplosionMath::exposureRayMiss)) {
                     visible++;
                  }

                  total++;
               }
            }
         }

         return total == 0 ? 0.0F : (float)visible / total;
      } else {
         return 0.0F;
      }
   }

   public static float exposure(class_1937 level, class_243 source, class_1297 entity, class_2338 phantom) {
      return exposure(level, source, entity, null, phantom);
   }

   private static Boolean clipExposureBlock(ExplosionMath.ExposureRayContext ray, class_2338 pos) {
      class_265 shape = pos.equals(ray.ignored)
         ? class_259.method_1073()
         : (pos.equals(ray.phantom) ? class_259.method_1077() : ray.level.method_8320(pos).method_26194(ray.level, pos, ray.collision));
      return shape.method_1092(ray.start, ray.source, pos) != null ? Boolean.TRUE : null;
   }

   private static Boolean exposureRayMiss(ExplosionMath.ExposureRayContext ignored) {
      return Boolean.FALSE;
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static final class ExposureRayContext {
      private final class_1937 level;
      private final class_243 source;
      private final class_3726 collision;
      private final class_2338 ignored;
      private final class_2338 phantom;
      private class_243 start;

      private ExposureRayContext(class_1937 level, class_243 source, class_3726 collision, class_2338 ignored, class_2338 phantom) {
         this.level = level;
         this.source = source;
         this.collision = collision;
         this.ignored = ignored;
         this.phantom = phantom;
      }
   }
}
