package dev.leonetic.util;

import dev.leonetic.Homovore2;
import dev.leonetic.mixin.ChatComponentAccessor;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_11719;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_310;
import net.minecraft.class_338;
import net.minecraft.class_5250;
import net.minecraft.class_7469;
import net.minecraft.class_11719.class_11721;

public final class ChatUtil {
   private static final class_11719 LOGO_FONT = new class_11721(Homovore2.id("logo"));
   private static final String LOGO_GLYPH = "\ue000";
   private static final int LOGO_COLOR = 16777215;
   private static final Map<String, class_7469> PERSISTENT = new HashMap<>();
   private static final SecureRandom RNG = new SecureRandom();

   private ChatUtil() {
   }

   public static class_5250 prefix() {
      return class_2561.method_43473().method_10852(clientName());
   }

   public static class_2561 clientName() {
      return class_2561.method_43470("\ue000").method_27696(class_2583.field_24360.method_27704(LOGO_FONT)).method_54663(16777215);
   }

   public static class_5250 withPrefix(String format, Object... args) {
      return withPrefix(TextUtil.text(format, args));
   }

   public static class_5250 withPrefix(class_2561 component) {
      class_5250 text = prefix();
      if (component != null) {
         text.method_27693(" ").method_10852(component);
      }

      return text;
   }

   public static void send(String format, Object... args) {
      send(TextUtil.text(format, args));
   }

   public static void send(class_2561 component) {
      raw(withPrefix(component));
   }

   public static void raw(class_2561 component) {
      class_338 chat = chat();
      if (chat != null) {
         chat.method_1812(component);
      }
   }

   public static void sendPersistent(String key, class_2561 component) {
      rawPersistent(key, withPrefix(component));
   }

   public static void rawPersistent(String key, class_2561 component) {
      class_338 chat = chat();
      if (chat != null) {
         ChatComponentAccessor accessor = (ChatComponentAccessor)chat;
         class_7469 previous = PERSISTENT.remove(key);
         if (previous != null && accessor.homovore$getAllMessages().removeIf(line -> previous.equals(line.comp_915()))) {
            accessor.homovore$refreshTrimmedMessages();
         }

         byte[] bytes = new byte[256];
         RNG.nextBytes(bytes);
         class_7469 signature = new class_7469(bytes);
         accessor.homovore$addMessage(component, signature, null);
         PERSISTENT.put(key, signature);
      }
   }

   public static void clearPersistent() {
      PERSISTENT.clear();
   }

   private static class_338 chat() {
      class_310 mc = class_310.method_1551();
      return mc.field_1705 == null ? null : mc.field_1705.method_1743();
   }
}
