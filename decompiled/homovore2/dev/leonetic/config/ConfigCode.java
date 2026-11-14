package dev.leonetic.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ConfigCode {
   private static final String PREFIX = "HV2-";
   private static final String VERSION_KEY = "version";
   private static final int VERSION = 1;
   private static final int MAX_INFLATED_BYTES = 16777216;

   private ConfigCode() {
   }

   public static String encode(JsonObject sections) {
      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      root.add("configs", sections);
      byte[] json = root.toString().getBytes(StandardCharsets.UTF_8);
      Deflater deflater = new Deflater(9);
      deflater.setInput(json);
      deflater.finish();
      ByteArrayOutputStream out = new ByteArrayOutputStream(json.length / 4 + 64);
      byte[] buffer = new byte[8192];

      while (!deflater.finished()) {
         out.write(buffer, 0, deflater.deflate(buffer));
      }

      deflater.end();
      return "HV2-" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
   }

   public static boolean looksLikeCode(String text) {
      return text != null && text.strip().startsWith("HV2-");
   }

   public static JsonObject decode(String code) {
      String trimmed = code == null ? "" : code.replaceAll("\\s+", "");
      if (!trimmed.startsWith("HV2-")) {
         throw new IllegalArgumentException("Not a Homovore config code");
      }

      byte[] compressed;
      try {
         compressed = Base64.getUrlDecoder().decode(trimmed.substring("HV2-".length()));
      } catch (IllegalArgumentException e) {
         throw new IllegalArgumentException("Config code is corrupted");
      }

      Inflater inflater = new Inflater();
      inflater.setInput(compressed);
      ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
      byte[] buffer = new byte[8192];

      try {
         while (!inflater.finished()) {
            int read = inflater.inflate(buffer);
            if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
               throw new IllegalArgumentException("Config code is incomplete");
            }

            out.write(buffer, 0, read);
            if (out.size() > 16777216) {
               throw new IllegalArgumentException("Config code is too large");
            }
         }
      } catch (DataFormatException e) {
         throw new IllegalArgumentException("Config code is corrupted");
      } finally {
         inflater.end();
      }

      JsonElement root;
      try {
         root = JsonParser.parseString(out.toString(StandardCharsets.UTF_8));
      } catch (RuntimeException e) {
         throw new IllegalArgumentException("Config code is corrupted");
      }

      if (!root.isJsonObject()) {
         throw new IllegalArgumentException("Config code is corrupted");
      }

      JsonObject object = root.getAsJsonObject();
      JsonElement version = object.get("version");
      if (version != null && version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()) {
         if (version.getAsInt() > 1) {
            throw new IllegalArgumentException("Config code is from a newer version of the client");
         } else {
            JsonElement configs = object.get("configs");
            if (configs != null && configs.isJsonObject()) {
               return configs.getAsJsonObject();
            } else {
               throw new IllegalArgumentException("Config code is corrupted");
            }
         }
      } else {
         throw new IllegalArgumentException("Config code is corrupted");
      }
   }
}
