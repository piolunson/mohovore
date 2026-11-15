package dev.piolunson.features.gui.items.buttons;

import dev.piolunson.features.settings.Bind;
import dev.piolunson.features.settings.Setting;
import java.awt.Color;

public abstract class SettingButton<T> extends Button {
   protected final Setting<T> setting;

   protected SettingButton(Setting<T> setting) {
      super(setting.getName());
      this.setting = setting;
   }

   @Override
   public void update() {
      this.setHidden(!this.setting.isVisible());
   }

   @Override
   public String getPage() {
      return this.setting.getPage();
   }

   public Setting<T> getSetting() {
      return this.setting;
   }

   public static SettingButton<?> create(Setting<?> setting) {
      if (setting.isColorSetting()) {
         return new ColorButton((Setting<Color>)setting);
      } else if (setting.isNumberSetting() && setting.hasRestriction()) {
         return new Slider((Setting<Number>)setting);
      } else if (setting.isEnumSetting()) {
         return new EnumButton((Setting<Enum<?>>)setting);
      } else {
         Object v = setting.getValue();
         if (v instanceof Boolean) {
            return new BooleanButton((Setting<Boolean>)setting);
         } else if (v instanceof Bind) {
            return new BindButton((Setting<Bind>)setting);
         } else {
            return v instanceof String ? new StringButton((Setting<String>)setting) : null;
         }
      }
   }
}
