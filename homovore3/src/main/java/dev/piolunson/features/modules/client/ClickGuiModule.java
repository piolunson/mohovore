package dev.piolunson.features.modules.client;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.ClientEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.gui.HomovoreGui;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Bind;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.ColorUtil;
import dev.piolunson.util.render.font.Fonts;
import java.awt.Color;

public class ClickGuiModule extends Module {
   private static ClickGuiModule INSTANCE;
   public Setting<String> prefix = this.str("Prefix", ".");
   public Setting<ClickGuiModule.Theme> theme = this.mode("Theme", ClickGuiModule.Theme.HOMOVORE);
   public Setting<Color> customColor = this.color("Custom Color", 120, 170, 210, 220);
   public Setting<Boolean> smooth = this.bool("Smooth", true);
   public Setting<Integer> rainbowHue = this.num("Delay", 240, 0, 600);
   public Setting<Float> rainbowBrightness = this.num("Brightness", 200.0F, 1.0F, 255.0F);
   public Setting<Float> rainbowSaturation = this.num("Saturation", 140.0F, 1.0F, 255.0F);
   public Setting<Boolean> clickGuiFont = this.bool("ClickGUI Font", false);
   public Setting<Boolean> hudFont = this.bool("HUD Font", false);
   public Setting<String> fontName = this.str("Font Name", "");
   private static final Color CAT_COMBAT = new Color(196, 88, 90);
   private static final Color CAT_WORLD = new Color(118, 168, 118);
   private static final Color CAT_RENDER = new Color(98, 166, 200);
   private static final Color CAT_MOVEMENT = new Color(212, 142, 78);
   private static final Color CAT_PLAYER = new Color(202, 180, 92);
   private static final Color CAT_FUNNY = new Color(198, 124, 178);
   private static final Color CAT_CLIENT = new Color(70, 75, 82);
   private static final Color CAT_HUD = new Color(96, 142, 200);
   private static final Color HOMOVORE_ACCENT = new Color(140, 26, 38);
   private static final Color HOMOVORE_MODULE = new Color(190, 72, 82);

   public Color categoryAccent(Module.Category cat) {
      return this.categoryAccent(cat, 0.0F);
   }

   public Color categoryAccent(Module.Category cat, float yOffset) {
      ClickGuiModule.Theme t = this.theme.getValue();
      if (t == ClickGuiModule.Theme.RAINBOW) {
         return this.rainbowAt(yOffset);
      }

      if (t == ClickGuiModule.Theme.CUSTOM) {
         return this.customColor.getValue();
      }

      if (t == ClickGuiModule.Theme.HOMOVORE) {
         return HOMOVORE_ACCENT;
      }

      if (cat == null) {
         return CAT_CLIENT;
      }

      switch (cat) {
         case COMBAT:
            return CAT_COMBAT;
         case WORLD:
            return CAT_WORLD;
         case RENDER:
            return CAT_RENDER;
         case MOVEMENT:
            return CAT_MOVEMENT;
         case PLAYER:
            return CAT_PLAYER;
         case FUNNY:
            return CAT_FUNNY;
         case CLIENT:
            return CAT_CLIENT;
         case HUD:
            return CAT_HUD;
         default:
            return CAT_CLIENT;
      }
   }

   public Color moduleAccent(Module.Category cat, float yOffset) {
      return this.theme.getValue() == ClickGuiModule.Theme.HOMOVORE ? HOMOVORE_MODULE : this.categoryAccent(cat, yOffset);
   }

   public Color chatAccent() {
      ClickGuiModule.Theme t = this.theme.getValue();
      if (t == ClickGuiModule.Theme.RAINBOW) {
         return this.rainbowAt(0.0F);
      } else if (t == ClickGuiModule.Theme.CUSTOM) {
         return this.customColor.getValue();
      } else {
         return t == ClickGuiModule.Theme.HOMOVORE ? HOMOVORE_ACCENT : Color.WHITE;
      }
   }

   public Color rainbowAt(float yOffset) {
      return ColorUtil.rainbow((int)(yOffset / 10.0F * this.rainbowHue.getValue().intValue()));
   }

   public float getExpandSpeed() {
      return this.smooth.getValue() ? 0.5F : 1.0F;
   }

   public ClickGuiModule() {
      super("ClickGui", "Opens the ClickGui", Module.Category.CLIENT);
      this.bind.setValue(new Bind(344));
      this.bindMode.setVisibility(v -> false);
      this.rainbowHue.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.rainbowBrightness.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.rainbowSaturation.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.customColor.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.CUSTOM);
      this.fontName.setVisibility(v -> this.clickGuiFont.getValue() || this.hudFont.getValue());
      INSTANCE = this;
   }

   @Subscribe
   public void onSettingChange(ClientEvent event) {
      if (event.getType() == ClientEvent.Type.SETTING_UPDATE && event.getSetting().getFeature().equals(this)) {
         if (event.getSetting().equals(this.prefix)) {
            Homovore.commandManager.setCommandPrefix(this.prefix.getPlannedValue());
            Command.sendMessage("Prefix set to {global} %s", Homovore.commandManager.getCommandPrefix());
         }

         if (event.getSetting().equals(this.clickGuiFont) || event.getSetting().equals(this.hudFont) || event.getSetting().equals(this.fontName)) {
            Fonts.markDirty();
         }
      }
   }

   @Override
   public void onEnable() {
      if (!nullCheck()) {
         mc.setScreen(HomovoreGui.getClickGui());
      }
   }

   @Override
   public void onLoad() {
      Color fixedAccent = new Color(120, 175, 220, 220);
      Homovore.colorManager.register("ui", () -> fixedAccent);
      Homovore.colorManager.register("chat", this::chatAccent);
      Homovore.colorManager.register("chatBracket", this::chatAccent);
      Homovore.commandManager.setCommandPrefix(this.prefix.getValue());
   }

   @Override
   public void onTick() {
      if (!(mc.currentScreen instanceof HomovoreGui)) {
         this.disable();
      }
   }

   public static ClickGuiModule getInstance() {
      return INSTANCE;
   }

   public enum Theme {
      HOMOVORE,
      COLORS,
      CUSTOM,
      RAINBOW;
   }
}
