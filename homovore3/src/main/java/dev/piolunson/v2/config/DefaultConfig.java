package dev.piolunson.v2.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class DefaultConfig {
   private static final String RESOURCE = "/homovore-2/default-config.json";
   private static final JsonObject SECTIONS = load();

   private DefaultConfig() {
   }

   public static JsonElement section(String fileName) {
      JsonElement section = SECTIONS.get(fileName);
      return section == null ? null : section.deepCopy();
   }

   private static JsonObject load() {
      try (InputStream stream = DefaultConfig.class.getResourceAsStream("/homovore-2/default-config.json")) {
         if (stream == null) {
            throw new IllegalStateException("Missing bundled default config /homovore-2/default-config.json");
         }

         try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
               throw new IllegalStateException("Bundled default config is not a JSON object");
            } else {
               return parsed.getAsJsonObject();
            }
         }
      } catch (IOException exception) {
         throw new IllegalStateException("Could not read bundled default config /homovore-2/default-config.json", exception);
      }
   }
}
