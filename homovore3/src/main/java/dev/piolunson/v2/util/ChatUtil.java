package dev.piolunson.v2.util;

import dev.piolunson.v2.Homovore2;
import dev.piolunson.v2.mixin.ChatComponentAccessor;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.StyleSpriteSource.Font;

public final class ChatUtil {
   private static final StyleSpriteSource LOGO_FONT = new Font(Homovore2.id("logo"));
   private static final String LOGO_GLYPH = "\ue000";
   private static final int LOGO_COLOR = 16777215;
   private static final Map<String, MessageSignatureData> PERSISTENT = new HashMap<>();
   private static final SecureRandom RNG = new SecureRandom();

   private ChatUtil() {
   }

   public static MutableText prefix() {
      return Text.empty().append(clientName());
   }

   public static Text clientName() {
      return Text.literal("\ue000").fillStyle(Style.EMPTY.withFont(LOGO_FONT)).withColor(16777215);
   }

   public static MutableText withPrefix(String format, Object... args) {
      return withPrefix(TextUtil.text(format, args));
   }

   public static MutableText withPrefix(Text component) {
      MutableText text = prefix();
      if (component != null) {
         text.append(" ").append(component);
      }

      return text;
   }

   public static void send(String format, Object... args) {
      send(TextUtil.text(format, args));
   }

   public static void send(Text component) {
      raw(withPrefix(component));
   }

   public static void raw(Text component) {
      ChatHud chat = chat();
      if (chat != null) {
         chat.addMessage(component);
      }
   }

   public static void sendPersistent(String key, Text component) {
      rawPersistent(key, withPrefix(component));
   }

   public static void rawPersistent(String key, Text component) {
      ChatHud chat = chat();
      if (chat != null) {
         ChatComponentAccessor accessor = (ChatComponentAccessor)chat;
         MessageSignatureData previous = PERSISTENT.remove(key);
         if (previous != null && accessor.homovore$getAllMessages().removeIf(line -> previous.equals(line.signature()))) {
            accessor.homovore$refreshTrimmedMessages();
         }

         byte[] bytes = new byte[256];
         RNG.nextBytes(bytes);
         MessageSignatureData signature = new MessageSignatureData(bytes);
         accessor.homovore$addMessage(component, signature, null);
         PERSISTENT.put(key, signature);
      }
   }

   public static void clearPersistent() {
      PERSISTENT.clear();
   }

   private static ChatHud chat() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.inGameHud == null ? null : mc.inGameHud.getChatHud();
   }
}
