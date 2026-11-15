package dev.piolunson.v2.render;

import dev.piolunson.v2.Homovore2;
import dev.piolunson.v2.util.ChatUtil;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class PlayerBadges {
   private static final List<PlayerBadges.Provider> PROVIDERS = new CopyOnWriteArrayList<>();

   private PlayerBadges() {
   }

   public static void register(PlayerBadges.Provider provider) {
      PROVIDERS.add(provider);
   }

   public static void unregister(PlayerBadges.Provider provider) {
      PROVIDERS.remove(provider);
   }

   public static boolean available() {
      return !PROVIDERS.isEmpty();
   }

   public static PlayerBadges.Badge get(UUID id, String name) {
      if (id == null) {
         return null;
      }

      for (PlayerBadges.Provider provider : PROVIDERS) {
         try {
            PlayerBadges.Badge badge = provider.badge(id, name);
            if (badge != null) {
               return badge;
            }
         } catch (RuntimeException exception) {
            Homovore2.LOGGER.warn("Player badge provider failed", exception);
         }
      }

      return null;
   }

   public static Text decorate(Text name, PlayerBadges.Badge badge) {
      MutableText out = Text.empty();
      if (badge.logo()) {
         out.append(ChatUtil.clientName()).append(" ");
      }

      out.append(name);

      for (PlayerBadges.Part part : badge.parts()) {
         out.append(Text.literal(part.text()).withColor(part.color() & 16777215));
      }

      return out;
   }

   public record Badge(boolean logo, List<PlayerBadges.Part> parts) {
      public Badge {
         parts = List.copyOf(parts);
      }
   }

   public record Part(String text, int color) {
   }

   @FunctionalInterface
   public interface Provider {
      PlayerBadges.Badge badge(UUID var1, String var2);
   }
}
