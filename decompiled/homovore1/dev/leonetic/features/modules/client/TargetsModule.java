package dev.leonetic.features.modules.client;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.class_1296;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1439;
import net.minecraft.class_1456;
import net.minecraft.class_1493;
import net.minecraft.class_1510;
import net.minecraft.class_1528;
import net.minecraft.class_1545;
import net.minecraft.class_1548;
import net.minecraft.class_1549;
import net.minecraft.class_1550;
import net.minecraft.class_1551;
import net.minecraft.class_1559;
import net.minecraft.class_1560;
import net.minecraft.class_1564;
import net.minecraft.class_1571;
import net.minecraft.class_1576;
import net.minecraft.class_1577;
import net.minecraft.class_1581;
import net.minecraft.class_1584;
import net.minecraft.class_1589;
import net.minecraft.class_1590;
import net.minecraft.class_1593;
import net.minecraft.class_1604;
import net.minecraft.class_1606;
import net.minecraft.class_1613;
import net.minecraft.class_1614;
import net.minecraft.class_1621;
import net.minecraft.class_1627;
import net.minecraft.class_1628;
import net.minecraft.class_1632;
import net.minecraft.class_1634;
import net.minecraft.class_1639;
import net.minecraft.class_1640;
import net.minecraft.class_1641;
import net.minecraft.class_1642;
import net.minecraft.class_1657;
import net.minecraft.class_4466;
import net.minecraft.class_4760;
import net.minecraft.class_4836;
import net.minecraft.class_6053;
import net.minecraft.class_8949;
import net.minecraft.class_9254;

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

   public boolean isValidPlayerTarget(class_1297 entity) {
      return !(entity instanceof class_1657) ? false : this.isValidTarget(entity);
   }

   public boolean isValidTarget(class_1297 entity) {
      if (entity == mc.field_1724) {
         return false;
      } else if (!(entity instanceof class_1309 living)) {
         return false;
      } else {
         if (living.method_29504()) {
            return false;
         }

         if (!(entity instanceof class_1657 player)) {
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

   public static boolean isHostile(class_1297 e) {
      return !(e instanceof class_1548)
            && !(e instanceof class_1613)
            && !(e instanceof class_1627)
            && !(e instanceof class_1639)
            && (!(e instanceof class_1642) || e instanceof class_1590)
            && !(e instanceof class_1576)
            && !(e instanceof class_1551)
            && !(e instanceof class_1632)
            && !(e instanceof class_9254)
            && !(e instanceof class_1564)
            && !(e instanceof class_1604)
            && !(e instanceof class_1584)
            && !(e instanceof class_1634)
            && !(e instanceof class_8949)
            && !(e instanceof class_1545)
            && !(e instanceof class_1528)
            && !(e instanceof class_1510)
            && !(e instanceof class_1606)
            && !(e instanceof class_1577)
            && !(e instanceof class_1550)
            && !(e instanceof class_1571)
            && !(e instanceof class_4760)
            && !(e instanceof class_1641)
            && !(e instanceof class_1589)
            && !(e instanceof class_1614)
            && !(e instanceof class_1559)
            && !(e instanceof class_1621)
            && !(e instanceof class_1593)
            && !(e instanceof class_1581)
            && !(e instanceof class_1640)
         ? isNeutralEntityType(e) && isAggressiveNow(e)
         : true;
   }

   public static boolean isNeutral(class_1297 e) {
      return isNeutralEntityType(e) && !isAggressiveNow(e);
   }

   public static boolean isPassive(class_1297 e) {
      return e instanceof class_1296 || e instanceof class_1439 golem && golem.method_6496();
   }

   private static boolean isNeutralEntityType(class_1297 e) {
      return e instanceof class_1560
         || e instanceof class_4836
         || e instanceof class_1590
         || e instanceof class_1628
         || e instanceof class_1549
         || e instanceof class_1456
         || e instanceof class_1493 w && !w.method_6181()
         || e instanceof class_4466
         || e instanceof class_6053
         || e instanceof class_1439 g && !g.method_6496();
   }

   private static boolean isAggressiveNow(class_1297 e) {
      if (e instanceof class_1560 enderman) {
         return enderman.method_7028();
      } else if (e instanceof class_1590 piglin) {
         return piglin.method_6510();
      } else if (e instanceof class_4836 piglin) {
         return piglin.method_6510();
      } else if (e instanceof class_1628 spider) {
         return spider.method_6510();
      } else if (e instanceof class_1549) {
         return true;
      } else if (e instanceof class_1456 bear) {
         return bear.method_6510();
      } else if (e instanceof class_1493 wolf) {
         return wolf.method_6510();
      } else {
         return e instanceof class_4466 bee ? bee.method_29511() : false;
      }
   }
}
