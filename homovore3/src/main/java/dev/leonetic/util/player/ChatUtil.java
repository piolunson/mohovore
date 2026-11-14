package dev.leonetic.util.player;

import dev.leonetic.Homovore;
import dev.leonetic.features.commands.Command;
import dev.leonetic.mixin.client.ChatComponentAccessor;
import dev.leonetic.util.traits.Util;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.util.Identifier;

public class ChatUtil {
   private static final Map<String, MessageSignatureData> PERSISTENT = new HashMap<>();
   private static final SecureRandom RNG = new SecureRandom();
   public static volatile Visible noAnimateHead;
   private static final StyleSpriteSource LOGO_FONT = new Font(Identifier.of("homovore", "logo"));

   public static void sendMessage(Text message) {
      sendSilentMessage(prefixed(message));
   }

   public static void sendSilentMessage(Text message) {
      if (!Command.nullCheck()) {
         Util.mc.inGameHud.getChatHud().addMessage(message);
      }
   }

   public static void sendPersistent(String key, Text message) {
      addPersistent(key, prefixed(message));
   }

   private static void removeSilently(MessageSignatureData signature) {
      if (!Command.nullCheck()) {
         ChatHud hud = Util.mc.inGameHud.getChatHud();
         ChatComponentAccessor accessor = (ChatComponentAccessor)hud;
         boolean removed = accessor.homovore$getAllMessages().removeIf(line -> signature.equals(line.signature()));
         if (removed) {
            accessor.homovore$refreshTrimmedMessages();
         }
      }
   }

   public static Text getClientNameComponent() {
      return Text.literal("\ue000").fillStyle(Style.EMPTY.withFont(LOGO_FONT)).withColor(Homovore.colorManager.getAsInt("chat"));
   }

   public static Text getSkullComponent() {
      return Text.literal("☠").withColor(16733525);
   }

   public static Text getInfoComponent() {
      return Text.literal("ℹ").withColor(16777045);
   }

   public static void sendPrefixed(Text icon, Text message) {
      sendSilentMessage(prefixedWith(icon, message));
   }

   public static void sendPersistentPrefixed(String key, Text icon, Text message) {
      addPersistent(key, prefixedWith(icon, message));
   }

   private static void addPersistent(String key, Text component) {
      if (!Command.nullCheck()) {
         MessageSignatureData previous = PERSISTENT.remove(key);
         if (previous != null) {
            removeSilently(previous);
         }

         byte[] data = new byte[256];
         RNG.nextBytes(data);
         MessageSignatureData signature = new MessageSignatureData(data);
         Util.mc.inGameHud.getChatHud().addMessage(component, signature, null);
         PERSISTENT.put(key, signature);
         List<Visible> trimmed = ((ChatComponentAccessor)Util.mc.inGameHud.getChatHud()).homovore$getTrimmedMessages();
         noAnimateHead = trimmed.isEmpty() ? null : trimmed.get(0);
      }
   }

   private static Text prefixedWith(Text icon, Text message) {
      return Text.empty()
         .append(Text.literal("[").withColor(Homovore.colorManager.getAsInt("chatBracket")))
         .append(icon)
         .append(Text.literal("]").withColor(Homovore.colorManager.getAsInt("chatBracket")))
         .append(Text.literal(" ").withColor(Homovore.colorManager.getAsInt("chatBracket")))
         .append(message);
   }

   private static Text prefixed(Text message) {
      return Text.empty()
         .append(Text.literal("[").withColor(Homovore.colorManager.getAsInt("chatBracket")))
         .append(getClientNameComponent())
         .append(Text.literal("]").withColor(Homovore.colorManager.getAsInt("chatBracket")))
         .append(Text.literal(" ").withColor(Homovore.colorManager.getAsInt("chatBracket")))
         .append(message);
   }
}
