package dev.piolunson.v2.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.piolunson.v2.Homovore2;
import dev.piolunson.v2.config.ConfigManager;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.util.Jsonable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SettingGroup implements Jsonable {
   private final List<Setting<?>> settings = new ArrayList<>();
   private Module owner;

   protected <S extends Setting<?>> S register(S setting) {
      setting.setOwner(this.owner);
      this.settings.add(setting);
      return setting;
   }

   public List<Setting<?>> getSettings() {
      return Collections.unmodifiableList(this.settings);
   }

   void setOwner(Module owner) {
      this.owner = owner;
      this.settings.forEach(setting -> setting.setOwner(owner));
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Setting<?> setting : this.settings) {
         try {
            object.add(setting.getName(), ConfigManager.valueToJson(setting));
         } catch (Throwable throwable) {
            Homovore2.LOGGER.error("Failed to save nested setting '{}'", setting.getName(), throwable);
         }
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();

         for (Setting<?> setting : this.settings) {
            try {
               ConfigManager.setValueFromJson(this.owner, setting, object.get(setting.getName()));
            } catch (Throwable throwable) {
               Homovore2.LOGGER.error("Failed to load nested setting '{}'", setting.getName(), throwable);
            }
         }
      }
   }
}
