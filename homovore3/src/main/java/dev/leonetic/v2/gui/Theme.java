package dev.leonetic.v2.gui;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.impl.ClickGuiModule;
import java.awt.Color;

public final class Theme {
   public static final int PANEL_WIDTH = 110;
   public static final int HEADER_HEIGHT = 13;
   public static final int PANEL_SPACING = 5;
   public static final int HEADER_GAP = 2;
   public static final int MODULE_HEIGHT = 13;
   public static final int SETTING_HEIGHT = 12;
   public static final int TEXT_PAD = 4;
   public static final int SETTING_INDENT = 8;
   public static final int SETTING_RIGHT_PAD = 2;
   public static final int BODY_BG = new Color(0, 0, 0, 140).getRGB();
   public static final int SCREEN_DIM = new Color(0, 0, 0, 110).getRGB();
   public static final int SEPARATOR = new Color(255, 255, 255, 36).getRGB();
   public static final int HOVER = new Color(255, 255, 255, 22).getRGB();
   public static final int OUTLINE = new Color(0, 0, 0, 160).getRGB();
   public static final int OUTLINE_INNER = new Color(0, 0, 0, 90).getRGB();
   public static final int HIGHLIGHT_TOP = new Color(255, 255, 255, 28).getRGB();
   public static final int TEXT_HEADER = -1;
   public static final int TEXT_MODULE = -1;
   public static final int TEXT_SETTING = -1644050;
   public static final int TEXT_SETTING_VALUE = -3880238;
   public static final int SLIDER_TRACK = new Color(255, 255, 255, 36).getRGB();

   private Theme() {
   }

   public static Color categoryColor(Category category) {
      return categoryColor(category, 0.0F);
   }

   public static Color categoryColor(Category category, float yOffset) {
      ClickGuiModule module = Homovore2Client.modules().get(ClickGuiModule.class);
      return module == null ? new Color(70, 75, 82) : module.categoryAccent(category, yOffset);
   }

   public static Color moduleColor(Category category, float yOffset) {
      ClickGuiModule module = Homovore2Client.modules().get(ClickGuiModule.class);
      return module == null ? new Color(70, 75, 82) : module.moduleAccent(category, yOffset);
   }

   public static Color brighten(Color color, float amount) {
      return new Color(
         (int)Math.min(255.0F, color.getRed() + (255 - color.getRed()) * amount),
         (int)Math.min(255.0F, color.getGreen() + (255 - color.getGreen()) * amount),
         (int)Math.min(255.0F, color.getBlue() + (255 - color.getBlue()) * amount),
         color.getAlpha()
      );
   }

   public static Color withAlpha(Color color, int alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
   }
}
