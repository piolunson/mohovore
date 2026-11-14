package dev.leonetic.util.world;

import java.util.Objects;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class AnchorDamageUtil {
   public static final double POWER = 5.0;
   public static final double EFFECT_RADIUS = 10.0;

   private AnchorDamageUtil() {
      throw new AssertionError();
   }

   public static double maxDamage(World level, PlayerEntity target, Vec3d source) {
      return damage(level, target, source, 1.0F);
   }

   public static double damage(World level, PlayerEntity target, Vec3d source, BlockPos anchorPos) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(source, "source");
      if (target.getAbilities().invulnerable) {
         return 0.0;
      }

      double distanceSquared = target.squaredDistanceTo(source);
      if (distanceSquared >= 100.0) {
         return 0.0;
      }

      float exposure = anchorPos == null ? ExplosionMath.exposure(level, source, target) : ExplosionMath.exposureIgnoring(level, source, target, anchorPos);
      return damage(level, target, source, exposure);
   }

   private static double damage(World level, PlayerEntity target, Vec3d source, float exposure) {
      return ExplosionMath.damage(
         5.0,
         target.squaredDistanceTo(source),
         exposure,
         target.getArmor(),
         target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
         ExplosionMath.resistanceFactor(target),
         ExplosionMath.protectionPoints(target),
         level.getDifficulty().getId()
      );
   }
}
