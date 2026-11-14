package dev.leonetic.features.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.Homovore;
import dev.leonetic.event.impl.ClientEvent;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.features.Feature;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.settings.Bind;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.ConfigManager;
import dev.leonetic.util.traits.Jsonable;
import dev.leonetic.util.traits.Toggleable;
import java.awt.Color;
import net.minecraft.class_124;
import org.joml.Vector2f;

public class Module extends Feature implements Jsonable, Toggleable {
   private final String description;
   private final Module.Category category;
   public final Setting<Boolean> enabled = this.bool("Enabled", false).setPage("General");
   public final Setting<Bind> bind = this.key("Keybind", new Bind(-1)).setPage("General");
   public final Setting<Module.BindMode> bindMode = this.mode("BindMode", Module.BindMode.TOGGLE).setPage("General");
   public final Setting<String> displayName;
   public boolean hidden;

   public Module(String name, String description, Module.Category category) {
      super(name);
      this.displayName = this.str("DisplayName", name).setPage("General");
      this.description = description;
      this.category = category;
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void onToggle() {
   }

   public void onLoad() {
   }

   public void onTick() {
   }

   public void onRender2D(Render2DEvent event) {
   }

   public void onRender3D(Render3DEvent event) {
   }

   public void onUnload() {
   }

   public String getDisplayInfo() {
      return null;
   }

   public String getMeta() {
      return null;
   }

   @Override
   public void enable() {
      this.enabled.setValue(true);
      EVENT_BUS.register(this);
      EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
      this.onToggle();
      this.onEnable();
   }

   @Override
   public void disable() {
      this.enabled.setValue(false);
      EVENT_BUS.unregister(this);
      EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
      this.onToggle();
      this.onDisable();
   }

   public String getDisplayName() {
      return this.displayName.getValue();
   }

   public void setDisplayName(String name) {
      Module module = Homovore.moduleManager.getModuleByDisplayName(name);
      Module originalModule = Homovore.moduleManager.getModuleByName(name);
      if (module == null && originalModule == null) {
         Command.sendMessage(this.getDisplayName() + ", name: " + this.getName() + ", has been renamed to: " + name);
         this.displayName.setValue(name);
      } else {
         Command.sendMessage("{red} A module of this name already exists.");
      }
   }

   @Override
   public boolean isEnabled() {
      return this.enabled.getValue();
   }

   @Override
   public boolean isToggled() {
      return this.isEnabled();
   }

   public String getDescription() {
      return this.description;
   }

   public Module.Category getCategory() {
      return this.category;
   }

   public String getInfo() {
      return null;
   }

   public Bind getBind() {
      return this.bind.getValue();
   }

   public void setBind(int key) {
      this.bind.setValue(new Bind(key));
   }

   public String getFullArrayString() {
      return this.getDisplayName()
         + class_124.field_1080
         + (this.getDisplayInfo() != null ? " [" + class_124.field_1068 + this.getDisplayInfo() + class_124.field_1080 + "]" : "");
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Setting<?> setting : this.getSettings()) {
         try {
            if (setting.getValue() instanceof Bind keyBind) {
               object.addProperty(setting.getName(), keyBind.getKey());
            } else if (setting.getValue() instanceof Color color) {
               object.addProperty(setting.getName(), color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha());
            } else if (setting.getValue() instanceof Vector2f pos) {
               object.addProperty(setting.getName(), pos.x() + "," + pos.y());
            } else {
               object.addProperty(setting.getName(), setting.getValueAsString());
            }
         } catch (Throwable e) {
            Homovore.LOGGER.error("Failed to create JSON field", e);
         }
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && !element.isJsonNull()) {
         JsonObject object = element.getAsJsonObject();
         if (object.has("Enabled")) {
            try {
               if (Boolean.parseBoolean(object.get("Enabled").getAsString())) {
                  this.toggle();
               }
            } catch (Throwable e) {
               Homovore.LOGGER.error("Failed to toggle module {} during load", this.getName(), e);
            }
         }

         for (Setting<?> setting : this.getSettings()) {
            if (setting != this.enabled) {
               try {
                  JsonElement settingElement = object.get(setting.getName());
                  if (settingElement != null && !settingElement.isJsonNull()) {
                     ConfigManager.setValueFromJson(this, setting, settingElement);
                  }
               } catch (Throwable throwable) {
                  Homovore.LOGGER.error("Failed to load from JSON", throwable);
               }
            }
         }
      }
   }

   public Module.BindMode getBindMode() {
      return this.bindMode.getValue();
   }

   public enum BindMode {
      TOGGLE,
      HOLD;
   }

   public enum Category {
      COMBAT("Combat"),
      WORLD("World"),
      RENDER("Render"),
      MOVEMENT("Movement"),
      PLAYER("Player"),
      FUNNY("Funny"),
      CLIENT("Client"),
      HUD("Hud");

      private final String name;

      Category(String name) {
         this.name = name;
      }

      public String getName() {
         return this.name;
      }
   }
}
