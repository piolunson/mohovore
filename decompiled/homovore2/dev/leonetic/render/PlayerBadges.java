package dev.leonetic.render;

import dev.leonetic.Homovore2;
import dev.leonetic.util.ChatUtil;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_2561;
import net.minecraft.class_5250;

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

   public static class_2561 decorate(class_2561 name, PlayerBadges.Badge badge) {
      class_5250 out = class_2561.method_43473();
      if (badge.logo()) {
         out.method_10852(ChatUtil.clientName()).method_27693(" ");
      }

      out.method_10852(name);

      for (PlayerBadges.Part part : badge.parts()) {
         out.method_10852(class_2561.method_43470(part.text()).method_54663(part.color() & 16777215));
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
