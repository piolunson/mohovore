package dev.leonetic.util.player;

import dev.leonetic.Homovore;
import dev.leonetic.features.commands.Command;
import dev.leonetic.mixin.client.ChatComponentAccessor;
import dev.leonetic.util.traits.Util;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_11719;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_2960;
import net.minecraft.class_338;
import net.minecraft.class_7469;
import net.minecraft.class_11719.class_11721;
import net.minecraft.class_303.class_7590;

public class ChatUtil {
   private static final Map<String, class_7469> PERSISTENT = new HashMap<>();
   private static final SecureRandom RNG = new SecureRandom();
   public static volatile class_7590 noAnimateHead;
   private static final class_11719 LOGO_FONT = new class_11721(class_2960.method_60655("homovore", "logo"));

   public static void sendMessage(class_2561 message) {
      sendSilentMessage(prefixed(message));
   }

   public static void sendSilentMessage(class_2561 message) {
      if (!Command.nullCheck()) {
         Util.mc.field_1705.method_1743().method_1812(message);
      }
   }

   public static void sendPersistent(String key, class_2561 message) {
      addPersistent(key, prefixed(message));
   }

   private static void removeSilently(class_7469 signature) {
      if (!Command.nullCheck()) {
         class_338 hud = Util.mc.field_1705.method_1743();
         ChatComponentAccessor accessor = (ChatComponentAccessor)hud;
         boolean removed = accessor.homovore$getAllMessages().removeIf(line -> signature.equals(line.comp_915()));
         if (removed) {
            accessor.homovore$refreshTrimmedMessages();
         }
      }
   }

   public static class_2561 getClientNameComponent() {
      return class_2561.method_43470("\ue000")
         .method_27696(class_2583.field_24360.method_27704(LOGO_FONT))
         .method_54663(Homovore.colorManager.getAsInt("chat"));
   }

   public static class_2561 getSkullComponent() {
      return class_2561.method_43470("☠").method_54663(16733525);
   }

   public static class_2561 getInfoComponent() {
      return class_2561.method_43470("ℹ").method_54663(16777045);
   }

   public static void sendPrefixed(class_2561 icon, class_2561 message) {
      sendSilentMessage(prefixedWith(icon, message));
   }

   public static void sendPersistentPrefixed(String key, class_2561 icon, class_2561 message) {
      addPersistent(key, prefixedWith(icon, message));
   }

   private static void addPersistent(String key, class_2561 component) {
      if (!Command.nullCheck()) {
         class_7469 previous = PERSISTENT.remove(key);
         if (previous != null) {
            removeSilently(previous);
         }

         byte[] data = new byte[256];
         RNG.nextBytes(data);
         class_7469 signature = new class_7469(data);
         Util.mc.field_1705.method_1743().method_44811(component, signature, null);
         PERSISTENT.put(key, signature);
         List<class_7590> trimmed = ((ChatComponentAccessor)Util.mc.field_1705.method_1743()).homovore$getTrimmedMessages();
         noAnimateHead = trimmed.isEmpty() ? null : trimmed.get(0);
      }
   }

   private static class_2561 prefixedWith(class_2561 icon, class_2561 message) {
      return class_2561.method_43473()
         .method_10852(class_2561.method_43470("[").method_54663(Homovore.colorManager.getAsInt("chatBracket")))
         .method_10852(icon)
         .method_10852(class_2561.method_43470("]").method_54663(Homovore.colorManager.getAsInt("chatBracket")))
         .method_10852(class_2561.method_43470(" ").method_54663(Homovore.colorManager.getAsInt("chatBracket")))
         .method_10852(message);
   }

   private static class_2561 prefixed(class_2561 message) {
      return class_2561.method_43473()
         .method_10852(class_2561.method_43470("[").method_54663(Homovore.colorManager.getAsInt("chatBracket")))
         .method_10852(getClientNameComponent())
         .method_10852(class_2561.method_43470("]").method_54663(Homovore.colorManager.getAsInt("chatBracket")))
         .method_10852(class_2561.method_43470(" ").method_54663(Homovore.colorManager.getAsInt("chatBracket")))
         .method_10852(message);
   }
}
