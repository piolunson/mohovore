package dev.leonetic.feature.setting;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.class_1299;
import net.minecraft.class_1792;
import net.minecraft.class_2378;
import net.minecraft.class_2396;
import net.minecraft.class_2591;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class RegistryKind<T> {
   public static final RegistryKind<class_1299<?>> ENTITY = new RegistryKind<>(class_7923.field_41177, entity -> entity.method_5897().getString());
   public static final RegistryKind<class_2396<?>> PARTICLE = new RegistryKind<>(
      class_7923.field_41180, particle -> spellOut(class_7923.field_41180.method_10221(particle))
   );
   public static final RegistryKind<class_2591<?>> BLOCK_ENTITY = new RegistryKind<>(
      class_7923.field_41181, blockEntity -> spellOut(class_7923.field_41181.method_10221(blockEntity))
   );
   public static final RegistryKind<class_1792> ITEM = new RegistryKind<>(class_7923.field_41178, item -> item.method_7864(item.method_7854()).getString());
   private final class_2378<T> registry;
   private final Function<T, String> naming;
   private List<T> entries;

   public RegistryKind(class_2378<T> registry, Function<T, String> naming) {
      this.registry = registry;
      this.naming = naming;
   }

   public List<T> getEntries() {
      if (this.entries == null) {
         this.entries = this.registry.method_10220().sorted(Comparator.comparing(this.naming, String.CASE_INSENSITIVE_ORDER)).toList();
      }

      return this.entries;
   }

   public String displayName(T entry) {
      return this.naming.apply(entry);
   }

   public String getKey(T entry) {
      class_2960 key = this.registry.method_10221(entry);
      return key == null ? "" : key.toString();
   }

   private static String spellOut(class_2960 identifier) {
      if (identifier == null) {
         return "";
      }

      StringBuilder name = new StringBuilder(identifier.method_12832().length());

      for (String word : identifier.method_12832().split("[._/]")) {
         if (!word.isEmpty()) {
            if (!name.isEmpty()) {
               name.append(' ');
            }

            name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
         }
      }

      return name.toString();
   }

   public Optional<T> byKey(String key) {
      class_2960 identifier = class_2960.method_12829(key);
      return identifier == null ? Optional.empty() : this.registry.method_17966(identifier);
   }
}
