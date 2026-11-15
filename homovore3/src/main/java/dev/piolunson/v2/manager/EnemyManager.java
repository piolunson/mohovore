package dev.piolunson.v2.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.util.Jsonable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;

public final class EnemyManager implements Jsonable {
   private final Map<String, String> enemies = new LinkedHashMap<>();

   public synchronized boolean isEnemy(String name) {
      return name != null && this.enemies.containsKey(key(name));
   }

   public boolean isEnemy(PlayerEntity player) {
      return player != null && this.isEnemy(player.getGameProfile().name());
   }

   public void addEnemy(String name) {
      synchronized (this) {
         this.enemies.put(key(name), name);
      }

      Homovore2Client.friends().removeFriend(name);
   }

   public synchronized boolean removeEnemy(String name) {
      return this.enemies.remove(key(name)) != null;
   }

   public synchronized void clearEnemies() {
      this.enemies.clear();
   }

   public synchronized List<String> getEnemies() {
      return new ArrayList<>(this.enemies.values());
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT);
   }

   @Override
   public synchronized JsonElement toJson() {
      JsonArray array = new JsonArray();
      this.enemies.values().forEach(array::add);
      JsonObject object = new JsonObject();
      object.add("enemies", array);
      return object;
   }

   @Override
   public synchronized void fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonElement stored = element.getAsJsonObject().get("enemies");
         if (stored != null && stored.isJsonArray()) {
            this.enemies.clear();

            for (JsonElement e : stored.getAsJsonArray()) {
               if (e.isJsonPrimitive()) {
                  String name = e.getAsString();
                  this.enemies.put(key(name), name);
               }
            }
         }
      }
   }

   @Override
   public String getFileName() {
      return "enemies.json";
   }

   @Override
   public boolean isShareable() {
      return false;
   }
}
