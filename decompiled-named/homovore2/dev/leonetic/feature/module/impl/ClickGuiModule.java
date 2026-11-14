package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.command.impl.ConfigCommand;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.feature.setting.ButtonSetting;
import dev.leonetic.feature.setting.ListSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.gui.ClickGuiScreen;
import dev.leonetic.render.font.FontLibrary;
import java.awt.Color;

public class ClickGuiModule extends Module {
   private static final int CATEGORY_ALPHA = 214;
   private static final Color NEUTRAL_ACCENT = new Color(58, 62, 70, 214);
   public final Setting<String> prefix = this.string("Prefix", ".");
   public final Setting<ClickGuiModule.Theme> theme = this.mode("Theme", ClickGuiModule.Theme.COLORS);
   public final Setting<Color> customColor = this.color("Custom Color", 120, 170, 210, 220);
   public final Setting<Boolean> smooth = this.bool("Smooth", true);
   public final Setting<Integer> rainbowHue = this.integer("Delay", 240, 0, 600);
   public final Setting<Float> rainbowBrightness = this.number("Brightness", 200.0F, 1.0F, 255.0F);
   public final Setting<Float> rainbowSaturation = this.number("Saturation", 140.0F, 1.0F, 255.0F);
   public final Setting<Boolean> clickGuiFont = this.bool("ClickGUI Font", false);
   public final Setting<Boolean> hudFont = this.bool("HUD Font", false);
   public final ListSetting fontName = this.list("Font", "None", FontLibrary::names).refreshWith(FontLibrary::rescanFolder);
   public final Setting<Integer> fontSize = this.integer("Font Size", 9, 6, 24);
   public final ButtonSetting exportConfig = this.button("Export Config", () -> ConfigCommand.exportToClipboard(false));
   public final ButtonSetting exportFunctional = this.button("Export Functional", ConfigCommand::exportFunctionalToClipboard);
   public final ButtonSetting importConfig = this.button("Import Config", () -> ConfigCommand.importCode(mc.keyboard.getClipboard()));

   public ClickGuiModule() {
      super("ClickGui", Category.CLIENT, "Opens the ClickGui");
      this.keybind.setValue(new Bind(344));
      this.rainbowHue.visibleWhen(() -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.rainbowBrightness.visibleWhen(() -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.rainbowSaturation.visibleWhen(() -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.customColor.visibleWhen(() -> this.theme.getValue() == ClickGuiModule.Theme.CUSTOM);
      this.fontName.visibleWhen(this::usesCustomFont);
      this.fontSize.visibleWhen(this::usesCustomFont);
   }

   private boolean usesCustomFont() {
      return this.clickGuiFont.getValue() || this.hudFont.getValue();
   }

   @Override
   public boolean persistsEnabled() {
      return false;
   }

   @Override
   public void onEnable() {
      mc.setScreen(ClickGuiScreen.get());
   }

   @Override
   public void onTick() {
      if (!(mc.currentScreen instanceof ClickGuiScreen)) {
         this.setEnabled(false);
      }
   }

   public Color categoryAccent(Category category, float yOffset) {
      if (this.theme.getValue() == ClickGuiModule.Theme.RAINBOW) {
         return this.rainbowAt(yOffset);
      }

      if (this.theme.getValue() == ClickGuiModule.Theme.CUSTOM) {
         return this.customColor.getValue();
      }

      if (this.theme.getValue() == ClickGuiModule.Theme.HOMOVORE) {
         return new Color(140, 26, 38);
      }

      if (category == null) {
         return NEUTRAL_ACCENT;
      }

      Color base = category.getColor();
      return new Color(base.getRed(), base.getGreen(), base.getBlue(), 214);
   }

   public Color moduleAccent(Category category, float yOffset) {
      return this.theme.getValue() == ClickGuiModule.Theme.HOMOVORE ? new Color(190, 72, 82) : this.categoryAccent(category, yOffset);
   }

   private Color rainbowAt(float yOffset) {
      double state = Math.ceil((System.currentTimeMillis() + (long)(yOffset / 10.0F * this.rainbowHue.getValue().intValue())) / 20.0);
      return Color.getHSBColor((float)(state % 360.0 / 360.0), this.rainbowSaturation.getValue() / 255.0F, this.rainbowBrightness.getValue() / 255.0F);
   }

   public float getExpandSpeed() {
      return this.smooth.getValue() ? 0.5F : 1.0F;
   }

   @Override
   public boolean isPersonal() {
      return true;
   }

   public enum Theme {
      HOMOVORE,
      COLORS,
      CUSTOM,
      RAINBOW;
   }
}
