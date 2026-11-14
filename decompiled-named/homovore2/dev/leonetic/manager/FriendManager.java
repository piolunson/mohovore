package dev.leonetic.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.Homovore2Client;
import dev.leonetic.util.Jsonable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;

public final class FriendManager implements Jsonable {
   public static final long PERMANENT = -1L;
   private final Map<String, FriendManager.Friend> friends = new LinkedHashMap<>();

   public synchronized boolean isFriend(String name) {
      if (name == null) {
         return false;
      }

      FriendManager.Friend friend = this.friends.get(key(name));
      return friend != null && !friend.expired(System.currentTimeMillis());
   }

   public boolean isFriend(PlayerEntity player) {
      return player != null && this.isFriend(player.getGameProfile().name());
   }

   public void addFriend(String name) {
      this.addFriend(name, -1L);
   }

   public void addFriend(String name, long expiry) {
      boolean alreadyFriend;
      synchronized (this) {
         alreadyFriend = this.isFriend(name);
         this.friends.put(key(name), new FriendManager.Friend(name, expiry));
      }

      Homovore2Client.enemies().removeEnemy(name);
      if (!alreadyFriend) {
         whisperAdded(name);
      }
   }

   private static void whisperAdded(String name) {
      ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
      if (connection != null) {
         connection.sendChatCommand("w " + name + " Added as a friend on client");
      }
   }

   public synchronized boolean removeFriend(String name) {
      return this.friends.remove(key(name)) != null;
   }

   public synchronized void clearFriends() {
      this.friends.clear();
   }

   public synchronized long getExpiry(String name) {
      FriendManager.Friend friend = this.friends.get(key(name));
      return friend == null ? -1L : friend.expiry();
   }

   public synchronized List<String> getFriends() {
      this.purgeExpired();
      List<String> names = new ArrayList<>(this.friends.size());

      for (FriendManager.Friend friend : this.friends.values()) {
         names.add(friend.name());
      }

      return names;
   }

   private void purgeExpired() {
      long now = System.currentTimeMillis();
      Iterator<FriendManager.Friend> it = this.friends.values().iterator();

      while (it.hasNext()) {
         if (it.next().expired(now)) {
            it.remove();
         }
      }
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT);
   }

   @Override
   public synchronized JsonElement toJson() {
      this.purgeExpired();
      JsonArray array = new JsonArray();

      for (FriendManager.Friend friend : this.friends.values()) {
         JsonObject entry = new JsonObject();
         entry.addProperty("name", friend.name());
         entry.addProperty("expiry", friend.expiry());
         array.add(entry);
      }

      JsonObject object = new JsonObject();
      object.add("friends", array);
      return object;
   }

   @Override
   public synchronized void fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonElement stored = element.getAsJsonObject().get("friends");
         if (stored != null && stored.isJsonArray()) {
            this.friends.clear();

            for (JsonElement e : stored.getAsJsonArray()) {
               if (e.isJsonObject()) {
                  JsonObject entry = e.getAsJsonObject();
                  if (entry.has("name")) {
                     String name = entry.get("name").getAsString();
                     long expiry = entry.has("expiry") ? entry.get("expiry").getAsLong() : -1L;
                     this.friends.put(key(name), new FriendManager.Friend(name, expiry));
                  }
               } else if (e.isJsonPrimitive()) {
                  String name = e.getAsString();
                  this.friends.put(key(name), new FriendManager.Friend(name, -1L));
               }
            }

            this.purgeExpired();
         }
      }
   }

   @Override
   public String getFileName() {
      return "friends.json";
   }

   @Override
   public boolean isShareable() {
      return false;
   }

   private record Friend(String name, long expiry) {
      boolean expired(long now) {
         return this.expiry != -1L && this.expiry <= now;
      }
   }
}
