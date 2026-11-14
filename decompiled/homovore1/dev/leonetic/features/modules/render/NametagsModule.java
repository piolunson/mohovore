package dev.leonetic.features.modules.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.DamageSyncTracker;
import dev.leonetic.util.render.MatrixCapture;
import dev.leonetic.util.traits.Jsonable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1657;
import net.minecraft.class_1684;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1842;
import net.minecraft.class_1844;
import net.minecraft.class_1847;
import net.minecraft.class_332;
import net.minecraft.class_6880;
import net.minecraft.class_9334;

public class NametagsModule extends Module {
   public Setting<Boolean> showPearls = this.bool("ShowPearls", true).setPage("Info");
   public Setting<Boolean> showItems = this.bool("ShowItems", true).setPage("Info");
   public Setting<Boolean> showArmor = this.bool("ShowArmor", true).setPage("Info");
   public Setting<Boolean> showDist = this.bool("ShowDist", true).setPage("Info");
   public Setting<Boolean> showPops = this.bool("ShowPops", true).setPage("Info");
   public Setting<Boolean> showResistance = this.bool("ShowResistance", true).setPage("Info");
   public Setting<Boolean> showStrength = this.bool("ShowStrength", true).setPage("Info");
   public Setting<Color> nameColor = this.color("NameColor", 255, 255, 255, 255).setPage("Colors");
   public Setting<Color> friendColor = this.color("FriendColor", 0, 255, 100, 255).setPage("Colors");
   public Setting<Color> enemyColor = this.color("EnemyColor", 255, 60, 60, 255).setPage("Colors");
   public Setting<Color> bgColor = this.color("BgColor", 0, 0, 0, 128).setPage("Colors");
   public Setting<Color> distColor = this.color("DistColor", 170, 170, 170, 255).setPage("Colors");
   public Setting<Color> popColor = this.color("PopColor", 255, 80, 80, 255).setPage("Colors");
   public Setting<Float> gap = this.num("Gap", 1.0F, 0.1F, 15.0F).setPage("Render");
   public Setting<Float> armorGap = this.num("ArmorGap", 2.0F, 0.0F, 20.0F).setPage("Render");
   public Setting<Float> scale = this.num("Scale", 1.0F, 0.1F, 3.0F).setPage("Render");
   public Setting<Float> minScale = this.num("MinScale", 0.3F, 0.1F, 1.0F).setPage("Render");
   public Setting<Boolean> itemsOnArmor = this.bool("ItemsOnArmor", false).setPage("Render");
   private static final long FIVE_DAYS_MS = 432000000L;
   private final Map<UUID, NametagsModule.PearlEntry> pearlOwnerCache = new HashMap<>();
   private final Jsonable pearlCacheJson = new Jsonable() {
      @Override
      public JsonElement toJson() {
         JsonObject root = new JsonObject();
         long now = System.currentTimeMillis();

         for (Entry<UUID, NametagsModule.PearlEntry> e : NametagsModule.this.pearlOwnerCache.entrySet()) {
            if (now - e.getValue().lastSeenMs() <= 432000000L) {
               JsonObject obj = new JsonObject();
               obj.addProperty("owner", e.getValue().ownerName());
               obj.addProperty("lastSeen", e.getValue().lastSeenMs());
               root.add(e.getKey().toString(), obj);
            }
         }

         return root;
      }

      @Override
      public void fromJson(JsonElement element) {
         if (element != null && !element.isJsonNull()) {
            long now = System.currentTimeMillis();

            for (Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
               try {
                  JsonObject obj = e.getValue().getAsJsonObject();
                  String owner = obj.get("owner").getAsString();
                  long lastSeen = obj.get("lastSeen").getAsLong();
                  if (now - lastSeen <= 432000000L) {
                     NametagsModule.this.pearlOwnerCache.put(UUID.fromString(e.getKey()), new NametagsModule.PearlEntry(owner, lastSeen));
                  }
               } catch (Exception var10) {
               }
            }
         }
      }

      @Override
      public String getFileName() {
         return "pearl_owners.json";
      }
   };
   static final class_1304[] ARMOR_SLOTS = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};
   private static final class_1799 TURTLE_MASTER_ICON = makeTurtleMasterIcon();
   private static final class_1799 GAPPLE_ICON = new class_1799(class_1802.field_8367);
   private static final class_1799 STRENGTH_ICON = makePotionIcon(class_1847.field_8978);

   private static class_1799 makeTurtleMasterIcon() {
      return makePotionIcon(class_1847.field_8990);
   }

   private static class_1799 makePotionIcon(class_6880<class_1842> potion) {
      class_1799 stack = new class_1799(class_1802.field_8436);
      stack.method_57379(class_9334.field_49651, new class_1844(potion));
      return stack;
   }

   public NametagsModule() {
      super("Nametags", "Renders custom nametags above players", Module.Category.RENDER);
      Homovore.configManager.addConfig(this.pearlCacheJson);
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck()) {
         if (MatrixCapture.projection != null) {
            class_332 graphics = event.getContext();
            float delta = event.getDelta();

            record RenderJob(double distSq, Runnable draw) {
            }

            List<RenderJob> jobs = new ArrayList<>();

            for (class_1657 player : mc.field_1687.method_18456()) {
               if (player != mc.field_1724 && player.method_5805()) {
                  double px = player.field_6014 + (player.method_23317() - player.field_6014) * delta;
                  double py = player.field_6036
                     + (player.method_23318() - player.field_6036) * delta
                     + player.method_17682()
                     + this.gap.getValue().floatValue() * 0.5;
                  double pz = player.field_5969 + (player.method_23321() - player.field_5969) * delta;
                  double dist = mc.field_1724.method_73189().method_1022(player.method_73189());
                  boolean isEnemy = Homovore.enemyManager.isEnemy(player);
                  boolean isFriend = !isEnemy && Homovore.friendManager.isFriend(player);
                  int nameArgb = (isEnemy ? this.enemyColor.getValue() : (isFriend ? this.friendColor.getValue() : this.nameColor.getValue())).getRGB();
                  String secondaryStr = this.showDist.getValue() ? " " + (int)dist + "m" : "";
                  int pops = Homovore.playerInfoManager.getTotemPops(player.method_5667());
                  Map<class_1304, class_1799> armor = new EnumMap<>(class_1304.class);

                  for (class_1304 slot : ARMOR_SLOTS) {
                     armor.put(slot, Homovore.playerInfoManager.getEquipment(player, slot));
                  }

                  class_1799 mainHand = Homovore.playerInfoManager.getMainHandItem(player);
                  class_1799 offHand = Homovore.playerInfoManager.getOffHandItem(player);
                  String name = player.method_7334().name();
                  List<class_1799> leadIcons = new ArrayList<>(2);
                  if (this.showResistance.getValue()) {
                     if (DamageSyncTracker.isTurtleMaster(player)) {
                        leadIcons.add(TURTLE_MASTER_ICON);
                     } else if (DamageSyncTracker.hasResistance(player)) {
                        leadIcons.add(GAPPLE_ICON);
                     }
                  }

                  if (this.showStrength.getValue() && DamageSyncTracker.hasStrength(player)) {
                     leadIcons.add(STRENGTH_ICON);
                  }

                  jobs.add(
                     new RenderJob(
                        dist * dist,
                        () -> this.renderNametag(graphics, px, py, pz, dist, name, nameArgb, secondaryStr, pops, armor, mainHand, offHand, leadIcons)
                     )
                  );
               }
            }

            if (this.showPearls.getValue()) {
               long now = System.currentTimeMillis();
               this.pearlOwnerCache.entrySet().removeIf(ex -> now - ((NametagsModule.PearlEntry)ex.getValue()).lastSeenMs() > 432000000L);

               for (class_1297 e : mc.field_1687.method_18112()) {
                  if (e instanceof class_1684 pearl && pearl.field_6012 > 2) {
                     UUID uuid = pearl.method_5667();
                     if (pearl.method_24921() instanceof class_1657 thrower) {
                        this.pearlOwnerCache.put(uuid, new NametagsModule.PearlEntry(thrower.method_7334().name(), now));
                     } else {
                        NametagsModule.PearlEntry existing = this.pearlOwnerCache.get(uuid);
                        if (existing != null) {
                           this.pearlOwnerCache.put(uuid, new NametagsModule.PearlEntry(existing.ownerName(), now));
                        }
                     }

                     NametagsModule.PearlEntry entry = this.pearlOwnerCache.get(uuid);
                     if (entry != null) {
                        double px = pearl.field_6014 + (pearl.method_23317() - pearl.field_6014) * delta;
                        double py = pearl.field_6036
                           + (pearl.method_23318() - pearl.field_6036) * delta
                           + pearl.method_17682()
                           + this.gap.getValue().floatValue() * 0.5;
                        double pz = pearl.field_5969 + (pearl.method_23321() - pearl.field_5969) * delta;
                        double dist = mc.field_1724.method_73189().method_1022(pearl.method_73189());
                        boolean isEnemy = Homovore.enemyManager.isEnemy(entry.ownerName());
                        boolean isFriend = !isEnemy && Homovore.friendManager.isFriend(entry.ownerName());
                        int nameArgb = (isEnemy ? this.enemyColor.getValue() : (isFriend ? this.friendColor.getValue() : this.nameColor.getValue())).getRGB();
                        String ownerName = entry.ownerName();
                        jobs.add(new RenderJob(dist * dist, () -> this.renderPearlTag(graphics, px, py, pz, dist, ownerName, nameArgb)));
                     }
                  }
               }
            }

            jobs.sort(Comparator.comparingDouble(RenderJob::distSq).reversed());

            for (RenderJob job : jobs) {
               job.draw.run();
            }
         }
      }
   }

   private void renderPearlTag(class_332 graphics, double wx, double wy, double wz, double dist, String name, int nameArgb) {
      float[] screen = MatrixCapture.worldToScreen(wx, wy, wz);
      if (screen != null) {
         float anchorX = screen[0];
         float anchorY = screen[1];
         float s = (float)Math.max(this.minScale.getValue().floatValue(), this.scale.getValue().floatValue() * 8.0 / (dist + 8.0));
         int nameW = mc.field_1772.method_1727(name);
         int halfW = nameW / 2;
         int textH = 9;
         int textTopY = -textH;
         graphics.method_51448().pushMatrix();
         graphics.method_51448().translate(anchorX, anchorY);
         graphics.method_51448().scale(s, s);
         graphics.method_25294(-halfW - 2, textTopY - 1, halfW + 2, 1, this.bgColor.getValue().getRGB());
         graphics.method_25303(mc.field_1772, name, -halfW, textTopY, nameArgb);
         graphics.method_51448().popMatrix();
      }
   }

   public void renderNametag(
      class_332 graphics,
      double wx,
      double wy,
      double wz,
      double dist,
      String name,
      int nameArgb,
      String secondaryStr,
      int totemPops,
      Map<class_1304, class_1799> armor,
      class_1799 mainHand,
      class_1799 offHand,
      List<class_1799> leadIcons
   ) {
      float[] screen = MatrixCapture.worldToScreen(wx, wy, wz);
      if (screen != null) {
         float anchorX = screen[0];
         float anchorY = screen[1];
         float s = (float)Math.max(this.minScale.getValue().floatValue(), this.scale.getValue().floatValue() * 8.0 / (dist + 8.0));
         int nameW = mc.field_1772.method_1727(name);
         int secondaryW = mc.field_1772.method_1727(secondaryStr);
         String popsStr = this.showPops.getValue() && totemPops > 0 ? " -" + totemPops : "";
         int popsW = mc.field_1772.method_1727(popsStr);
         int textH = 9;
         int textTopY = -textH;
         int iconCount = leadIcons == null ? 0 : leadIcons.size();
         int iconSize = iconCount > 0 ? textH : 0;
         int iconGap = 1;
         int leadW = iconCount * (iconSize + iconGap);
         int totalW = leadW + nameW + secondaryW + popsW;
         int halfW = totalW / 2;
         int nameX = -halfW + leadW;
         graphics.method_51448().pushMatrix();
         graphics.method_51448().translate(anchorX, anchorY);
         graphics.method_51448().scale(s, s);
         graphics.method_25294(-halfW - 2, textTopY - 1, halfW + 2, 1, this.bgColor.getValue().getRGB());
         if (iconCount > 0) {
            float itemScale = iconSize / 16.0F;
            int iconY = textTopY + (textH - iconSize) / 2;

            for (int k = 0; k < iconCount; k++) {
               graphics.method_51448().pushMatrix();
               graphics.method_51448().translate(-halfW + k * (iconSize + iconGap), iconY);
               graphics.method_51448().scale(itemScale, itemScale);
               graphics.method_51427(leadIcons.get(k), 0, 0);
               graphics.method_51448().popMatrix();
            }
         }

         graphics.method_25303(mc.field_1772, name, nameX, textTopY, nameArgb);
         if (!secondaryStr.isEmpty()) {
            graphics.method_25303(mc.field_1772, secondaryStr, nameX + nameW, textTopY, this.distColor.getValue().getRGB());
         }

         if (!popsStr.isEmpty()) {
            graphics.method_25303(mc.field_1772, popsStr, nameX + nameW + secondaryW, textTopY, this.popColor.getValue().getRGB());
         }

         if (this.showArmor.getValue()) {
            boolean hasArmor = false;

            for (class_1304 slot : ARMOR_SLOTS) {
               if (!armor.get(slot).method_7960()) {
                  hasArmor = true;
                  break;
               }
            }

            if (hasArmor) {
               int slotSize = 16;
               int armorTopY = textTopY - 1 - this.armorGap.getValue().intValue() - slotSize;

               for (int i = 0; i < 4; i++) {
                  class_1799 armorStack = armor.get(ARMOR_SLOTS[i]);
                  int slotX = -(slotSize * 4) / 2 + i * slotSize;
                  graphics.method_51448().pushMatrix();
                  graphics.method_51448().translate(slotX, armorTopY);
                  graphics.method_51427(armorStack, 0, 0);
                  graphics.method_51431(mc.field_1772, armorStack, 0, 0);
                  graphics.method_51448().popMatrix();
               }
            }
         }

         if (this.showItems.getValue()) {
            int slotSize = 16;
            int itemY;
            int offX;
            int mainX;
            if (this.itemsOnArmor.getValue()) {
               itemY = textTopY - 1 - this.armorGap.getValue().intValue() - slotSize;
               offX = -(slotSize * 4) / 2 - slotSize;
               mainX = slotSize * 4 / 2;
            } else {
               itemY = textTopY + textH / 2 - slotSize / 2;
               offX = -halfW - 2 - slotSize;
               mainX = halfW + 2;
            }

            if (!offHand.method_7960()) {
               graphics.method_51427(offHand, offX, itemY);
               graphics.method_51431(mc.field_1772, offHand, offX, itemY);
            }

            if (!mainHand.method_7960()) {
               graphics.method_51427(mainHand, mainX, itemY);
               graphics.method_51431(mc.field_1772, mainHand, mainX, itemY);
            }
         }

         graphics.method_51448().popMatrix();
      }
   }

   private record PearlEntry(String ownerName, long lastSeenMs) {
   }
}
