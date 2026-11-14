package dev.leonetic.features.modules.client;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.IllusionerEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;

public class TargetsModule extends Module {
   public final Setting<Boolean> players = this.bool("Players", true);
   public final Setting<Boolean> hostiles = this.bool("Hostiles", false);
   public final Setting<Boolean> neutrals = this.bool("Neutrals", false);
   public final Setting<Boolean> passives = this.bool("Passives", false);

   public TargetsModule() {
      super("Targets", "Configure which entities combat modules target.", Module.Category.CLIENT);
   }

   @Override
   public void onLoad() {
      if (!this.isEnabled()) {
         this.enable();
      }
   }

   @Override
   public void onDisable() {
      this.enable();
   }

   public boolean isValidPlayerTarget(Entity entity) {
      return !(entity instanceof PlayerEntity) ? false : this.isValidTarget(entity);
   }

   public boolean isValidTarget(Entity entity) {
      if (entity == mc.player) {
         return false;
      } else if (!(entity instanceof LivingEntity living)) {
         return false;
      } else {
         if (living.isDead()) {
            return false;
         }

         if (!(entity instanceof PlayerEntity player)) {
            if (this.hostiles.getValue() && isHostile(entity)) {
               return true;
            } else {
               return this.neutrals.getValue() && isNeutral(entity) ? true : this.passives.getValue() && isPassive(entity);
            }
         } else {
            return this.players.getValue() && !Homovore.friendManager.isFriend(player);
         }
      }
   }

   public static boolean isHostile(Entity e) {
      return !(e instanceof CreeperEntity)
            && !(e instanceof SkeletonEntity)
            && !(e instanceof StrayEntity)
            && !(e instanceof WitherSkeletonEntity)
            && (!(e instanceof ZombieEntity) || e instanceof ZombifiedPiglinEntity)
            && !(e instanceof HuskEntity)
            && !(e instanceof DrownedEntity)
            && !(e instanceof VindicatorEntity)
            && !(e instanceof BoggedEntity)
            && !(e instanceof EvokerEntity)
            && !(e instanceof PillagerEntity)
            && !(e instanceof RavagerEntity)
            && !(e instanceof VexEntity)
            && !(e instanceof BreezeEntity)
            && !(e instanceof BlazeEntity)
            && !(e instanceof WitherEntity)
            && !(e instanceof EnderDragonEntity)
            && !(e instanceof ShulkerEntity)
            && !(e instanceof GuardianEntity)
            && !(e instanceof ElderGuardianEntity)
            && !(e instanceof GhastEntity)
            && !(e instanceof HoglinEntity)
            && !(e instanceof ZombieVillagerEntity)
            && !(e instanceof MagmaCubeEntity)
            && !(e instanceof SilverfishEntity)
            && !(e instanceof EndermiteEntity)
            && !(e instanceof SlimeEntity)
            && !(e instanceof PhantomEntity)
            && !(e instanceof IllusionerEntity)
            && !(e instanceof WitchEntity)
         ? isNeutralEntityType(e) && isAggressiveNow(e)
         : true;
   }

   public static boolean isNeutral(Entity e) {
      return isNeutralEntityType(e) && !isAggressiveNow(e);
   }

   public static boolean isPassive(Entity e) {
      return e instanceof PassiveEntity || e instanceof IronGolemEntity golem && golem.isPlayerCreated();
   }

   private static boolean isNeutralEntityType(Entity e) {
      return e instanceof EndermanEntity
         || e instanceof PiglinEntity
         || e instanceof ZombifiedPiglinEntity
         || e instanceof SpiderEntity
         || e instanceof CaveSpiderEntity
         || e instanceof PolarBearEntity
         || e instanceof WolfEntity w && !w.isTamed()
         || e instanceof BeeEntity
         || e instanceof GoatEntity
         || e instanceof IronGolemEntity g && !g.isPlayerCreated();
   }

   private static boolean isAggressiveNow(Entity e) {
      if (e instanceof EndermanEntity enderman) {
         return enderman.isAngry();
      } else if (e instanceof ZombifiedPiglinEntity piglin) {
         return piglin.isAttacking();
      } else if (e instanceof PiglinEntity piglin) {
         return piglin.isAttacking();
      } else if (e instanceof SpiderEntity spider) {
         return spider.isAttacking();
      } else if (e instanceof CaveSpiderEntity) {
         return true;
      } else if (e instanceof PolarBearEntity bear) {
         return bear.isAttacking();
      } else if (e instanceof WolfEntity wolf) {
         return wolf.isAttacking();
      } else {
         return e instanceof BeeEntity bee ? bee.hasAngerTime() : false;
      }
   }
}
