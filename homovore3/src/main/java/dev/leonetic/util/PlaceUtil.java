package dev.leonetic.util;

import dev.leonetic.util.traits.Util;
import java.util.Iterator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class PlaceUtil implements Util {
   private PlaceUtil() {
      throw new AssertionError();
   }

   public static int minCell(double boxMin) {
      return MathHelper.floor(boxMin);
   }

   public static int maxCell(double boxMax) {
      return MathHelper.ceil(boxMax) - 1;
   }

   public static boolean canPlace(BlockPos pos) {
      if (mc.world.isOutOfHeightLimit(pos)) {
         return false;
      } else if (!mc.world.getBlockState(pos).isReplaceable()) {
         return false;
      } else if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > 5.154) {
         return false;
      } else {
         return mc.player.getBoundingBox().intersects(new Box(pos)) ? false : noEntityCollision(pos);
      }
   }

   public static boolean noEntityCollision(BlockPos pos) {
      Iterator var1 = mc.world
         .getEntitiesByClass(
            Entity.class,
            new Box(pos),
            ex -> !(ex instanceof ItemEntity)
               && !(ex instanceof ExperienceOrbEntity)
               && !(ex instanceof ExperienceBottleEntity)
               && !(ex instanceof FireworkRocketEntity)
               && !(ex instanceof EndCrystalEntity)
               && ex != mc.player
         )
         .iterator();
      if (var1.hasNext()) {
         Entity e = (Entity)var1.next();
         return false;
      } else {
         return true;
      }
   }
}
