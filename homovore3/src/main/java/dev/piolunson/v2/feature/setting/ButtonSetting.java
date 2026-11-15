package dev.piolunson.v2.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.piolunson.v2.util.Jsonable;

public final class ButtonSetting extends Setting<Boolean> implements Jsonable {
   private final Runnable action;

   public ButtonSetting(String name, Runnable action) {
      super(name, false);
      this.action = action;
   }

   public void press() {
      this.action.run();
   }

   @Override
   public String getType() {
      return "Button";
   }

   @Override
   public JsonElement toJson() {
      return JsonNull.INSTANCE;
   }

   @Override
   public void fromJson(JsonElement element) {
   }
}
