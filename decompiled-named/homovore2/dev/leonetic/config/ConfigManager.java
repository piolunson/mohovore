package dev.leonetic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.Jsonable;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("ConfigManager");
   private static final Path HOMOVORE_PATH = FabricLoader.getInstance().getGameDir().resolve("homovore");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final List<Jsonable> jsonables = new LinkedList<>();
   private final Set<Path> backedUpThisRun = new HashSet<>();
   private boolean loadFailed;

   public void addConfig(Jsonable jsonable) {
      this.jsonables.add(jsonable);
   }

   public void load() {
      this.mkdirs();
      this.loadFailed = false;

      for (Jsonable jsonable : this.jsonables) {
         Path file = HOMOVORE_PATH.resolve(jsonable.getFileName());
         if (!Files.exists(file)) {
            jsonable.fromJson(null);
         } else {
            try {
               String read = Files.readString(file);
               jsonable.fromJson(JsonParser.parseString(read));
            } catch (Throwable e) {
               this.loadFailed = true;
               LOGGER.error("Failed to load {}", jsonable.getFileName(), e);
            }
         }
      }
   }

   public synchronized void save() {
      if (this.loadFailed) {
         LOGGER.error("Refusing to overwrite configs because this session did not load them cleanly");
      } else {
         this.mkdirs();

         for (Jsonable jsonable : this.jsonables) {
            try {
               JsonElement json = jsonable.toJson();
               this.writeSafely(HOMOVORE_PATH.resolve(jsonable.getFileName()), GSON.toJson(json));
            } catch (Throwable e) {
               LOGGER.error("Failed to write to file {}", jsonable.getFileName(), e);
            }
         }
      }
   }

   private void writeSafely(Path file, String contents) throws IOException {
      Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
      Path backup = file.resolveSibling(file.getFileName() + ".bak");
      Files.writeString(temporary, contents);
      if (Files.exists(file) && this.backedUpThisRun.add(file)) {
         Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      }

      try {
         Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
         Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public String exportFunctionalCode() {
      JsonObject sections = new JsonObject();

      for (Jsonable jsonable : this.jsonables) {
         if (jsonable instanceof ModuleManager modules) {
            sections.add(modules.getFileName(), modules.toFunctionalJson());
         }
      }

      return ConfigCode.encode(sections);
   }

   public String exportCode(boolean includePersonal) {
      JsonObject sections = new JsonObject();

      for (Jsonable jsonable : this.jsonables) {
         if (includePersonal || jsonable.isShareable()) {
            try {
               sections.add(jsonable.getFileName(), jsonable.toJson());
            } catch (Throwable e) {
               LOGGER.error("Failed to export {}", jsonable.getFileName(), e);
            }
         }
      }

      return ConfigCode.encode(sections);
   }

   public List<String> importCode(String code) {
      JsonObject sections = ConfigCode.decode(code);
      List<String> applied = new LinkedList<>();

      for (Jsonable jsonable : this.jsonables) {
         JsonElement section = sections.get(jsonable.getFileName());
         if (section != null) {
            try {
               jsonable.fromJson(section);
               applied.add(jsonable.getFileName());
            } catch (Throwable e) {
               LOGGER.error("Failed to import {}", jsonable.getFileName(), e);
            }
         }
      }

      if (!applied.isEmpty()) {
         this.save();
      }

      return applied;
   }

   private void mkdirs() {
      if (!HOMOVORE_PATH.toFile().exists()) {
         boolean success = HOMOVORE_PATH.toFile().mkdirs();
         if (!success) {
            throw new RuntimeException("Failed to create needed directories!");
         }
      }
   }

   public static void setValueFromJson(Module module, Setting setting, JsonElement element) {
      if (element != null && !element.isJsonNull()) {
         if (setting instanceof Jsonable jsonable) {
            jsonable.fromJson(element);
         } else {
            switch (setting.getType()) {
               case "Boolean":
                  setting.setValue(element.getAsBoolean());
                  break;
               case "Double":
                  setting.setValue(element.getAsDouble());
                  break;
               case "Float":
                  setting.setValue(element.getAsFloat());
                  break;
               case "Integer":
                  setting.setValue(element.getAsInt());
                  break;
               case "String":
                  setting.setValue(element.getAsString());
                  break;
               case "Bind":
                  setting.setValue(new Bind(element.getAsInt()));
                  break;
               case "Color":
                  try {
                     String[] parts = element.getAsString().split(",");
                     if (parts.length == 4) {
                        setting.setValue(
                           new Color(
                              Integer.parseInt(parts[0].trim()),
                              Integer.parseInt(parts[1].trim()),
                              Integer.parseInt(parts[2].trim()),
                              Integer.parseInt(parts[3].trim())
                           )
                        );
                     }
                  } catch (Exception exception) {
                     LOGGER.error("Error parsing color for: {} : {}", module.getName(), setting.getName());
                  }
                  break;
               case "Pos":
                  try {
                     String[] parts = element.getAsString().split(",");
                     if (parts.length == 2) {
                        setting.setValue(new Vector2f(Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim())));
                     }
                  } catch (Exception exception) {
                     LOGGER.error("Error parsing position for: {} : {}", module.getName(), setting.getName());
                  }
                  break;
               case "Enum":
                  try {
                     Enum current = (Enum)setting.getValue();
                     setting.setValue(Enum.valueOf(current.getDeclaringClass(), element.getAsString()));
                  } catch (Exception exception) {
                     LOGGER.error("Error parsing enum for {}.{}: {}", new Object[]{module.getName(), setting.getName(), exception});
                  }
                  break;
               default:
                  LOGGER.error("Unknown Setting type for: {} : {}", module.getName(), setting.getName());
            }
         }
      }
   }

   public static JsonElement valueToJson(Setting<?> setting) {
      if (setting instanceof Jsonable jsonable) {
         return jsonable.toJson();
      } else {
         return switch (setting.getValue()) {
            case Bind bind -> GSON.toJsonTree(bind.getKey());
            case Color color -> GSON.toJsonTree(color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha());
            case Vector2f pos -> GSON.toJsonTree(pos.x() + "," + pos.y());
            default -> GSON.toJsonTree(setting.getValueAsString());
         };
      }
   }
}
