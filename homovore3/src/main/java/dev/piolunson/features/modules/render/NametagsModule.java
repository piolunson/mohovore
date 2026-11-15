package dev.piolunson.features.modules.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.piolunson.Homovore;
import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.DamageSyncTracker;
import dev.piolunson.util.render.MatrixCapture;
import dev.piolunson.util.traits.Jsonable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;

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
   static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final ItemStack TURTLE_MASTER_ICON = makeTurtleMasterIcon();
   private static final ItemStack GAPPLE_ICON = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
   private static final ItemStack STRENGTH_ICON = makePotionIcon(Potions.STRENGTH);

   private static ItemStack makeTurtleMasterIcon() {
      return makePotionIcon(Potions.TURTLE_MASTER);
   }

   private static ItemStack makePotionIcon(RegistryEntry<Potion> potion) {
      ItemStack stack = new ItemStack(Items.SPLASH_POTION);
      stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potion));
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
            DrawContext graphics = event.getContext();
            float delta = event.getDelta();

            record RenderJob(double distSq, Runnable draw) {
            }

            List<RenderJob> jobs = new ArrayList<>();

            for (PlayerEntity player : mc.world.getPlayers()) {
               if (player != mc.player && player.isAlive()) {
                  double px = player.lastX + (player.getX() - player.lastX) * delta;
                  double py = player.lastY + (player.getY() - player.lastY) * delta + player.getHeight() + this.gap.getValue().floatValue() * 0.5;
                  double pz = player.lastZ + (player.getZ() - player.lastZ) * delta;
                  double dist = mc.player.getEntityPos().distanceTo(player.getEntityPos());
                  boolean isEnemy = Homovore.enemyManager.isEnemy(player);
                  boolean isFriend = !isEnemy && Homovore.friendManager.isFriend(player);
                  int nameArgb = (isEnemy ? this.enemyColor.getValue() : (isFriend ? this.friendColor.getValue() : this.nameColor.getValue())).getRGB();
                  String secondaryStr = this.showDist.getValue() ? " " + (int)dist + "m" : "";
                  int pops = Homovore.playerInfoManager.getTotemPops(player.getUuid());
                  Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);

                  for (EquipmentSlot slot : ARMOR_SLOTS) {
                     armor.put(slot, Homovore.playerInfoManager.getEquipment(player, slot));
                  }

                  ItemStack mainHand = Homovore.playerInfoManager.getMainHandItem(player);
                  ItemStack offHand = Homovore.playerInfoManager.getOffHandItem(player);
                  String name = player.getGameProfile().name();
                  List<ItemStack> leadIcons = new ArrayList<>(2);
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

               for (Entity e : mc.world.getEntities()) {
                  if (e instanceof EnderPearlEntity pearl && pearl.age > 2) {
                     UUID uuid = pearl.getUuid();
                     if (pearl.getOwner() instanceof PlayerEntity thrower) {
                        this.pearlOwnerCache.put(uuid, new NametagsModule.PearlEntry(thrower.getGameProfile().name(), now));
                     } else {
                        NametagsModule.PearlEntry existing = this.pearlOwnerCache.get(uuid);
                        if (existing != null) {
                           this.pearlOwnerCache.put(uuid, new NametagsModule.PearlEntry(existing.ownerName(), now));
                        }
                     }

                     NametagsModule.PearlEntry entry = this.pearlOwnerCache.get(uuid);
                     if (entry != null) {
                        double px = pearl.lastX + (pearl.getX() - pearl.lastX) * delta;
                        double py = pearl.lastY + (pearl.getY() - pearl.lastY) * delta + pearl.getHeight() + this.gap.getValue().floatValue() * 0.5;
                        double pz = pearl.lastZ + (pearl.getZ() - pearl.lastZ) * delta;
                        double dist = mc.player.getEntityPos().distanceTo(pearl.getEntityPos());
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

   private void renderPearlTag(DrawContext graphics, double wx, double wy, double wz, double dist, String name, int nameArgb) {
      float[] screen = MatrixCapture.worldToScreen(wx, wy, wz);
      if (screen != null) {
         float anchorX = screen[0];
         float anchorY = screen[1];
         float s = (float)Math.max(this.minScale.getValue().floatValue(), this.scale.getValue().floatValue() * 8.0 / (dist + 8.0));
         int nameW = mc.textRenderer.getWidth(name);
         int halfW = nameW / 2;
         int textH = 9;
         int textTopY = -textH;
         graphics.getMatrices().pushMatrix();
         graphics.getMatrices().translate(anchorX, anchorY);
         graphics.getMatrices().scale(s, s);
         graphics.fill(-halfW - 2, textTopY - 1, halfW + 2, 1, this.bgColor.getValue().getRGB());
         graphics.drawTextWithShadow(mc.textRenderer, name, -halfW, textTopY, nameArgb);
         graphics.getMatrices().popMatrix();
      }
   }

   public void renderNametag(
      DrawContext graphics,
      double wx,
      double wy,
      double wz,
      double dist,
      String name,
      int nameArgb,
      String secondaryStr,
      int totemPops,
      Map<EquipmentSlot, ItemStack> armor,
      ItemStack mainHand,
      ItemStack offHand,
      List<ItemStack> leadIcons
   ) {
      float[] screen = MatrixCapture.worldToScreen(wx, wy, wz);
      if (screen != null) {
         float anchorX = screen[0];
         float anchorY = screen[1];
         float s = (float)Math.max(this.minScale.getValue().floatValue(), this.scale.getValue().floatValue() * 8.0 / (dist + 8.0));
         int nameW = mc.textRenderer.getWidth(name);
         int secondaryW = mc.textRenderer.getWidth(secondaryStr);
         String popsStr = this.showPops.getValue() && totemPops > 0 ? " -" + totemPops : "";
         int popsW = mc.textRenderer.getWidth(popsStr);
         int textH = 9;
         int textTopY = -textH;
         int iconCount = leadIcons == null ? 0 : leadIcons.size();
         int iconSize = iconCount > 0 ? textH : 0;
         int iconGap = 1;
         int leadW = iconCount * (iconSize + iconGap);
         int totalW = leadW + nameW + secondaryW + popsW;
         int halfW = totalW / 2;
         int nameX = -halfW + leadW;
         graphics.getMatrices().pushMatrix();
         graphics.getMatrices().translate(anchorX, anchorY);
         graphics.getMatrices().scale(s, s);
         graphics.fill(-halfW - 2, textTopY - 1, halfW + 2, 1, this.bgColor.getValue().getRGB());
         if (iconCount > 0) {
            float itemScale = iconSize / 16.0F;
            int iconY = textTopY + (textH - iconSize) / 2;

            for (int k = 0; k < iconCount; k++) {
               graphics.getMatrices().pushMatrix();
               graphics.getMatrices().translate(-halfW + k * (iconSize + iconGap), iconY);
               graphics.getMatrices().scale(itemScale, itemScale);
               graphics.drawItem(leadIcons.get(k), 0, 0);
               graphics.getMatrices().popMatrix();
            }
         }

         graphics.drawTextWithShadow(mc.textRenderer, name, nameX, textTopY, nameArgb);
         if (!secondaryStr.isEmpty()) {
            graphics.drawTextWithShadow(mc.textRenderer, secondaryStr, nameX + nameW, textTopY, this.distColor.getValue().getRGB());
         }

         if (!popsStr.isEmpty()) {
            graphics.drawTextWithShadow(mc.textRenderer, popsStr, nameX + nameW + secondaryW, textTopY, this.popColor.getValue().getRGB());
         }

         if (this.showArmor.getValue()) {
            boolean hasArmor = false;

            for (EquipmentSlot slot : ARMOR_SLOTS) {
               if (!armor.get(slot).isEmpty()) {
                  hasArmor = true;
                  break;
               }
            }

            if (hasArmor) {
               int slotSize = 16;
               int armorTopY = textTopY - 1 - this.armorGap.getValue().intValue() - slotSize;

               for (int i = 0; i < 4; i++) {
                  ItemStack armorStack = armor.get(ARMOR_SLOTS[i]);
                  int slotX = -(slotSize * 4) / 2 + i * slotSize;
                  graphics.getMatrices().pushMatrix();
                  graphics.getMatrices().translate(slotX, armorTopY);
                  graphics.drawItem(armorStack, 0, 0);
                  graphics.drawStackOverlay(mc.textRenderer, armorStack, 0, 0);
                  graphics.getMatrices().popMatrix();
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

            if (!offHand.isEmpty()) {
               graphics.drawItem(offHand, offX, itemY);
               graphics.drawStackOverlay(mc.textRenderer, offHand, offX, itemY);
            }

            if (!mainHand.isEmpty()) {
               graphics.drawItem(mainHand, mainX, itemY);
               graphics.drawStackOverlay(mc.textRenderer, mainHand, mainX, itemY);
            }
         }

         graphics.getMatrices().popMatrix();
      }
   }

   private record PearlEntry(String ownerName, long lastSeenMs) {
   }
}
