package dev.piolunson.v2.feature.setting;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class RegistryKind<T> {
   public static final RegistryKind<EntityType<?>> ENTITY = new RegistryKind<>(Registries.ENTITY_TYPE, entity -> entity.getName().getString());
   public static final RegistryKind<ParticleType<?>> PARTICLE = new RegistryKind<>(
      Registries.PARTICLE_TYPE, particle -> spellOut(Registries.PARTICLE_TYPE.getId(particle))
   );
   public static final RegistryKind<BlockEntityType<?>> BLOCK_ENTITY = new RegistryKind<>(
      Registries.BLOCK_ENTITY_TYPE, blockEntity -> spellOut(Registries.BLOCK_ENTITY_TYPE.getId(blockEntity))
   );
   public static final RegistryKind<Item> ITEM = new RegistryKind<>(Registries.ITEM, item -> item.getName(item.getDefaultStack()).getString());
   private final Registry<T> registry;
   private final Function<T, String> naming;
   private List<T> entries;

   public RegistryKind(Registry<T> registry, Function<T, String> naming) {
      this.registry = registry;
      this.naming = naming;
   }

   public List<T> getEntries() {
      if (this.entries == null) {
         this.entries = this.registry.stream().sorted(Comparator.comparing(this.naming, String.CASE_INSENSITIVE_ORDER)).toList();
      }

      return this.entries;
   }

   public String displayName(T entry) {
      return this.naming.apply(entry);
   }

   public String getKey(T entry) {
      Identifier key = this.registry.getId(entry);
      return key == null ? "" : key.toString();
   }

   private static String spellOut(Identifier identifier) {
      if (identifier == null) {
         return "";
      }

      StringBuilder name = new StringBuilder(identifier.getPath().length());

      for (String word : identifier.getPath().split("[._/]")) {
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
      Identifier identifier = Identifier.tryParse(key);
      return identifier == null ? Optional.empty() : this.registry.getOptionalValue(identifier);
   }
}
