package dev.leonetic.v2.feature.module.impl.hud;

import dev.leonetic.v2.feature.module.impl.HudModule;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.render.Render2D;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

public final class ArmorElement extends AnchoredElement {
   private static final int ICON = 16;
   private static final int STRIDE = 18;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final float TAG_SCALE = 0.8F;
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final ArmorElement.ProtectionTag[] TAGS = new ArmorElement.ProtectionTag[]{
      new ArmorElement.ProtectionTag(Enchantments.PROTECTION, "P"),
      new ArmorElement.ProtectionTag(Enchantments.BLAST_PROTECTION, "BP"),
      new ArmorElement.ProtectionTag(Enchantments.FIRE_PROTECTION, "FP"),
      new ArmorElement.ProtectionTag(Enchantments.PROJECTILE_PROTECTION, "PP")
   };
   public final Setting<Boolean> protectionTags = this.register(new Setting<>("Protection", true)).describe("Tags each piece with the protection it carries.");

   public ArmorElement() {
      super("Armor", true);
   }

   @Override
   public void render(DrawContext graphics, HudModule hud) {
      int width = SLOTS.length * 18 - 2;
      int left = graphics.getScaledWindowWidth() / 2 + 91 - width;
      int y = graphics.getScaledWindowHeight() - 39 - 1 - 16;
      if (showingBubbles()) {
         y -= 10;
      }

      for (int i = 0; i < SLOTS.length; i++) {
         ItemStack stack = mc.player.getEquippedStack(SLOTS[i]);
         if (!stack.isEmpty()) {
            int x = left + i * 18;
            graphics.drawItem(stack, x, y);
            graphics.drawStackOverlay(mc.textRenderer, stack, x, y);
            if (this.protectionTags.getValue()) {
               String tag = protectionTag(stack);
               if (tag != null) {
                  graphics.getMatrices().pushMatrix();
                  graphics.getMatrices().scale(0.8F, 0.8F);
                  Render2D.text(graphics, tag, Math.round(x / 0.8F), Math.round(y / 0.8F), -1, true);
                  graphics.getMatrices().popMatrix();
               }
            }
         }
      }
   }

   private static String protectionTag(ItemStack stack) {
      for (ArmorElement.ProtectionTag tag : TAGS) {
         for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (((RegistryEntry)entry.getKey()).matchesKey(tag.key())) {
               return tag.label();
            }
         }
      }

      return null;
   }

   private record ProtectionTag(RegistryKey<Enchantment> key, String label) {
   }
}
