package dev.piolunson.v2.gui.component;

import dev.piolunson.v2.feature.module.impl.hud.HudElementSetting;
import dev.piolunson.v2.feature.setting.Bind;
import dev.piolunson.v2.feature.setting.ButtonSetting;
import dev.piolunson.v2.feature.setting.ListSetting;
import dev.piolunson.v2.feature.setting.MultiListSetting;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.feature.setting.SoundSetting;
import java.awt.Color;

public abstract class SettingComponent<T> extends Component {
   protected final Setting<T> setting;

   protected SettingComponent(Setting<T> setting) {
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

   public static SettingComponent<?> of(Setting<?> setting) {
      if (setting instanceof ButtonSetting action) {
         return new ButtonComponent(action);
      } else if (setting instanceof HudElementSetting hudElement) {
         return new HudElementComponent(hudElement);
      } else if (setting instanceof ListSetting list) {
         return new ListComponent(list);
      } else if (setting instanceof MultiListSetting<?> list) {
         return new RegistryComponent<>(list);
      } else if (setting instanceof SoundSetting soundSetting) {
         return new SoundComponent(soundSetting);
      } else if (setting.isColorSetting()) {
         return new ColorComponent((Setting<Color>)setting);
      } else if (setting.isNumberSetting() && setting.isRanged()) {
         return new SliderComponent((Setting<Number>)setting);
      } else if (setting.isEnumSetting()) {
         return new EnumComponent((Setting<Enum<?>>)setting);
      } else {
         Object value = setting.getValue();
         if (value instanceof Boolean) {
            return new BooleanComponent((Setting<Boolean>)setting);
         } else if (value instanceof Bind) {
            return new BindComponent((Setting<Bind>)setting);
         } else {
            return value instanceof String ? new StringComponent((Setting<String>)setting) : null;
         }
      }
   }
}
