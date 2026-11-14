package dev.leonetic.features.modules.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.client.HudModule;
import dev.leonetic.features.modules.client.HudPosition;
import dev.leonetic.features.settings.Bind;
import dev.leonetic.util.traits.Jsonable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.class_332;

public class ActiveModulesHudModule extends HudModule implements Jsonable {
   private static final int STACK_GAP = 2;
   private static final int GRAY = -5592406;
   private static ActiveModulesHudModule INSTANCE;
   private final LinkedHashSet<String> entries = new LinkedHashSet<>();

   public ActiveModulesHudModule() {
      super("ActiveModules");
      INSTANCE = this;
      Homovore.configManager.addConfig(this);
   }

   public static ActiveModulesHudModule getInstance() {
      return INSTANCE;
   }

   public boolean add(String name) {
      Module module = Homovore.moduleManager.getModuleByName(name);
      return module == null ? false : this.entries.add(module.getName());
   }

   public boolean remove(String name) {
      Module module = Homovore.moduleManager.getModuleByName(name);
      String key = module != null ? module.getName() : name;
      return this.entries.removeIf(e -> e.equalsIgnoreCase(key));
   }

   public void clear() {
      this.entries.clear();
   }

   public List<String> getEntries() {
      return new ArrayList<>(this.entries);
   }

   @Override
   public void render(Render2DEvent event) {
      if (!this.entries.isEmpty()) {
         class_332 ctx = event.getContext();
         HudClientModule hudClient = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
         int activeColor = hudClient != null ? hudClient.activeModuleColor.getValue().getRGB() : Homovore.colorManager.getAsIntFullAlpha("chat");
         HudPosition pos = hudClient != null ? hudClient.positionOf(this) : HudPosition.CENTER_RIGHT;
         int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
         int gap = pos.isBottom() && linesBelow > 0 ? 2 : 0;
         int y = this.blockTop(pos, this.entries.size(), linesBelow, gap);

         for (String name : this.entries) {
            Module module = Homovore.moduleManager.getModuleByName(name);
            if (module != null) {
               String display = module.getDisplayName();
               String metaRaw = module.getMeta();
               String meta = metaRaw != null ? " (" + metaRaw + ")" : "";
               Bind bind = module.getBind().getKey() > 0 ? module.getBind() : null;
               String suffix = bind != null ? " [" + bind.toString() + "]" : "";
               int width = mc.field_1772.method_1727(display) + mc.field_1772.method_1727(meta) + mc.field_1772.method_1727(suffix);
               int x = this.lineX(pos, width);
               int nameColor = module.isEnabled() ? activeColor : -5592406;
               ctx.method_25303(mc.field_1772, display, x, y, nameColor);
               int cursor = x + mc.field_1772.method_1727(display);
               if (!meta.isEmpty()) {
                  ctx.method_25303(mc.field_1772, meta, cursor, y, -5592406);
                  cursor += mc.field_1772.method_1727(meta);
               }

               if (!suffix.isEmpty()) {
                  ctx.method_25303(mc.field_1772, suffix, cursor, y, activeColor);
               }

               y += 9;
            }
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();
      JsonArray array = new JsonArray();

      for (String entry : this.entries) {
         array.add(entry);
      }

      object.add("entries", array);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      this.entries.clear();
      JsonElement arr = element.getAsJsonObject().get("entries");
      if (arr != null && arr.isJsonArray()) {
         for (JsonElement e : arr.getAsJsonArray()) {
            this.entries.add(e.getAsString());
         }
      }
   }

   @Override
   public String getFileName() {
      return "active_modules_hud.json";
   }
}
