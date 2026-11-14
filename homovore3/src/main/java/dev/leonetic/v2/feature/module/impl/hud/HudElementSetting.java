package dev.leonetic.v2.feature.module.impl.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.config.ConfigManager;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.util.Jsonable;

public final class HudElementSetting extends Setting<Boolean> implements Jsonable {
   private final HudElement element;

   public HudElementSetting(HudElement element) {
      super(element.getName(), element.enabled.getDefaultValue());
      this.element = element;
      this.page("Elements");
      this.describe("Toggles this HUD element; right click to open its settings.");
   }

   public HudElement getElement() {
      return this.element;
   }

   public Boolean getValue() {
      return this.element.enabled.getValue();
   }

   public void setValue(Boolean value) {
      this.element.enabled.setValue(value);
   }

   public void setValueNoEvent(Boolean value) {
      this.element.enabled.setValueNoEvent(value);
   }

   @Override
   public String getValueAsString() {
      return String.valueOf(this.getValue());
   }

   @Override
   public String displayValue() {
      return String.valueOf(this.getValue());
   }

   @Override
   public String getType() {
      return "HudElement";
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Setting<?> setting : this.element.getSettings()) {
         try {
            object.add(setting.getName(), ConfigManager.valueToJson(setting));
         } catch (Throwable throwable) {
            Homovore2.LOGGER.error("Failed to save HUD setting '{}.{}'", new Object[]{this.element.getName(), setting.getName(), throwable});
         }
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonObject()) {
         JsonObject object = json.getAsJsonObject();

         for (Setting<?> setting : this.element.getSettings()) {
            try {
               ConfigManager.setValueFromJson(this.getOwner(), setting, object.get(setting.getName()));
            } catch (Throwable throwable) {
               Homovore2.LOGGER.error("Failed to load HUD setting '{}.{}'", new Object[]{this.element.getName(), setting.getName(), throwable});
            }
         }
      }
   }
}
