package dev.leonetic.v2.feature.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.config.ConfigManager;
import dev.leonetic.v2.event.impl.ClientEvent;
import dev.leonetic.v2.feature.setting.Bind;
import dev.leonetic.v2.feature.setting.ButtonSetting;
import dev.leonetic.v2.feature.setting.ListSetting;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.util.Jsonable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import org.joml.Vector2f;

public abstract class Module implements Jsonable {
   protected static final MinecraftClient mc = MinecraftClient.getInstance();
   private final String name;
   private final String displayName;
   private final String description;
   private final Category category;
   private final List<Setting<?>> settings = new ArrayList<>();
   public final Setting<Boolean> enabled = this.register(new Setting<>("Enabled", false));
   public final Setting<Bind> keybind = this.register(new Setting<>("Bind", new Bind(-1)));
   public final Setting<Module.BindMode> bindMode = this.register(new Setting<>("BindMode", Module.BindMode.TOGGLE));
   private static final Set<String> PERSONAL_PAGES = Set.of("render", "style", "chams", "particles");

   protected Module(String name, Category category, String description) {
      this.name = name;
      this.displayName = spaceCamelCase(name);
      this.category = category;
      this.description = description;
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void onTick() {
   }

   public int getTickPriority() {
      return 0;
   }

   public void onRender2D(DrawContext graphics, float partialTick) {
   }

   public void onRender3D(Camera camera, float partialTick) {
   }

   public final void toggle() {
      this.setEnabled(!this.isEnabled());
   }

   public final void setEnabled(boolean state) {
      if (state != this.isEnabled()) {
         this.enabled.setValue(state);
         if (this.isEnabled() == state) {
            if (state) {
               Homovore2Client.EVENT_BUS.register(this);
            } else {
               Homovore2Client.EVENT_BUS.unregister(this);
            }

            Homovore2Client.EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
            if (state) {
               this.onEnable();
            } else {
               this.onDisable();
            }
         }
      }
   }

   public final boolean isEnabled() {
      return this.enabled.getValue();
   }

   public String getName() {
      return this.name;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   private static String spaceCamelCase(String name) {
      return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
   }

   public String getDescription() {
      return this.description;
   }

   public Category getCategory() {
      return this.category;
   }

   public List<Setting<?>> getSettings() {
      return this.settings;
   }

   public List<Setting<?>> getGuiSettings() {
      List<Setting<?>> out = new ArrayList<>();

      for (Setting<?> setting : this.settings) {
         if (setting != this.enabled && setting != this.keybind && setting != this.bindMode) {
            out.add(setting);
         }
      }

      out.add(this.keybind);
      out.add(this.bindMode);
      return out;
   }

   public Module.BindMode getBindMode() {
      return this.bindMode.getValue();
   }

   protected <S extends Setting<?>> S register(S setting) {
      setting.setOwner(this);
      this.settings.add(setting);
      return setting;
   }

   public boolean persistsEnabled() {
      return true;
   }

   public boolean isPersonal() {
      Category category = this.getCategory();
      return category == Category.RENDER || category == Category.HUD || category.isPlugin();
   }

   public static boolean isPersonalSetting(Setting<?> setting) {
      if (setting instanceof ButtonSetting) {
         return true;
      }

      Object value = setting.getValue();
      if (!(value instanceof Bind) && !(value instanceof Module.BindMode) && !(value instanceof Color)) {
         if (setting.getName().toLowerCase(Locale.ROOT).endsWith("debug")) {
            return true;
         }

         String page = setting.getPage();
         return page != null && PERSONAL_PAGES.contains(page.toLowerCase(Locale.ROOT));
      } else {
         return true;
      }
   }

   public JsonObject toFunctionalJson() {
      JsonObject object = this.toJson().getAsJsonObject();

      for (Setting<?> setting : this.getSettings()) {
         if (isPersonalSetting(setting)) {
            object.remove(setting.getName());
         }
      }

      return object;
   }

   public boolean isEnabledByDefault() {
      return false;
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Setting<?> setting : this.getSettings()) {
         try {
            if (setting != this.enabled || this.persistsEnabled()) {
               if (setting instanceof Jsonable jsonable) {
                  object.add(setting.getName(), jsonable.toJson());
               } else {
                  switch (setting.getValue()) {
                     case Bind bind:
                        object.addProperty(setting.getName(), bind.getKey());
                        break;
                     case Color color:
                        object.addProperty(setting.getName(), color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha());
                        break;
                     case Vector2f pos:
                        object.addProperty(setting.getName(), pos.x() + "," + pos.y());
                        break;
                     default:
                        object.addProperty(setting.getName(), setting.getValueAsString());
                  }
               }
            }
         } catch (Throwable e) {
            Homovore2.LOGGER.error("Failed to create JSON field", e);
         }
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();

         for (Setting<?> setting : this.getSettings()) {
            if (setting != this.enabled) {
               try {
                  ConfigManager.setValueFromJson(this, setting, object.get(setting.getName()));
               } catch (Throwable throwable) {
                  Homovore2.LOGGER.error("Failed to load from JSON", throwable);
               }
            }
         }

         if (this.persistsEnabled() && object.has(this.enabled.getName())) {
            this.setEnabled(object.get(this.enabled.getName()).getAsBoolean());
         }
      } else {
         this.setEnabled(this.isEnabledByDefault());
      }
   }

   protected ButtonSetting button(String name, Runnable action) {
      return this.register(new ButtonSetting(name, action));
   }

   protected Setting<Boolean> bool(String name, boolean defaultValue) {
      return this.register(new Setting<>(name, defaultValue));
   }

   protected Setting<Integer> integer(String name, int defaultValue, int min, int max) {
      return this.register(new Setting<>(name, defaultValue, min, max));
   }

   protected Setting<Double> number(String name, double defaultValue, double min, double max) {
      return this.register(new Setting<>(name, defaultValue, min, max));
   }

   protected Setting<Float> number(String name, float defaultValue, float min, float max) {
      return this.register(new Setting<>(name, defaultValue, min, max));
   }

   protected Setting<String> string(String name, String defaultValue) {
      return this.register(new Setting<>(name, defaultValue));
   }

   protected ListSetting list(String name, String defaultValue, Supplier<List<String>> options) {
      return this.register(new ListSetting(name, defaultValue, options));
   }

   protected Setting<Color> color(String name, int r, int g, int b, int a) {
      return this.register(new Setting<>(name, new Color(r, g, b, a)));
   }

   protected <E extends Enum<E>> Setting<E> mode(String name, E defaultValue) {
      return this.register(new Setting<>(name, defaultValue));
   }

   public enum BindMode {
      TOGGLE,
      HOLD;
   }
}
