package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PotionEffectManager;
import dev.leonetic.render.Nametag;
import java.awt.Color;
import java.util.List;
import net.minecraft.class_332;

public final class PotIndicatorElement extends AnchoredElement {
   private static final int DEFAULT_OFFSET_X = 10;
   private static final int DEFAULT_OFFSET_Y = -4;
   private static final int ROW_GAP = 1;
   public final Setting<Color> timerColor = this.register(new Setting<>("Timer", new Color(255, 255, 255, 255)))
      .describe("The countdown beside each potion icon.");
   public final Setting<Integer> offsetX = this.register(new Setting<>("Offset X", 10, -200, 200)).describe("How far right of the crosshair the icons sit.");
   public final Setting<Integer> offsetY = this.register(new Setting<>("Offset Y", -4, -200, 200)).describe("How far below the crosshair the icons sit.");

   public PotIndicatorElement() {
      super("PotIndicator", false);
   }

   @Override
   public void render(class_332 graphics, HudModule hud) {
      List<PotionEffectManager.Icon> effects = Homovore2Client.potions().icons(mc.field_1724);
      if (!effects.isEmpty()) {
         int size = Nametag.potionSize();
         int x = graphics.method_51421() / 2 + this.offsetX.getValue();
         int y = graphics.method_51443() / 2 + this.offsetY.getValue();

         for (PotionEffectManager.Icon effect : effects) {
            Nametag.drawPotions(graphics, List.of(effect), x, y, size, this.timerColor.getValue().getRGB(), true);
            y += size + 1;
         }
      }
   }
}
