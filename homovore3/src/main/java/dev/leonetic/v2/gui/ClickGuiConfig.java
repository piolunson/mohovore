package dev.leonetic.v2.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.v2.config.DefaultConfig;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.util.Jsonable;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClickGuiConfig implements Jsonable {
   private static final Map<String, ClickGuiConfig.PanelState> STATES = new LinkedHashMap<>();

   public static void apply(Panel panel) {
      ClickGuiConfig.PanelState state = STATES.get(panel.getCategory().name());
      if (state != null) {
         panel.setPosition(state.x(), state.y());
         panel.setOpen(state.open());
      }
   }

   private static void capture() {
      for (Panel panel : ClickGuiScreen.livePanels()) {
         STATES.put(panel.getCategory().name(), new ClickGuiConfig.PanelState(panel.getX(), panel.getY(), panel.isOpen()));
      }
   }

   @Override
   public JsonElement toJson() {
      capture();
      JsonObject object = new JsonObject();
      STATES.forEach((category, state) -> {
         JsonObject entry = new JsonObject();
         entry.addProperty("Position", state.x() + "," + state.y());
         entry.addProperty("Open", state.open());
         object.add(category, entry);
      });
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element == null) {
         element = DefaultConfig.section(this.getFileName());
      }

      if (element != null && element.isJsonObject()) {
         STATES.clear();

         for (Category category : Category.values()) {
            JsonElement stored = element.getAsJsonObject().get(category.name());
            if (stored != null && stored.isJsonObject()) {
               JsonObject entry = stored.getAsJsonObject();
               String[] parts = entry.has("Position") ? entry.get("Position").getAsString().split(",") : new String[0];
               if (parts.length == 2) {
                  try {
                     STATES.put(
                        category.name(),
                        new ClickGuiConfig.PanelState(
                           Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), !entry.has("Open") || entry.get("Open").getAsBoolean()
                        )
                     );
                  } catch (NumberFormatException var10) {
                  }
               }
            }
         }

         ClickGuiScreen.livePanels().forEach(ClickGuiConfig::apply);
      }
   }

   @Override
   public String getFileName() {
      return "clickgui.json";
   }

   public record PanelState(int x, int y, boolean open) {
   }
}
