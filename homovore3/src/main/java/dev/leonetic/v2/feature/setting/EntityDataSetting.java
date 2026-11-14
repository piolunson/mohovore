package dev.leonetic.v2.feature.setting;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.entity.EntityType;

public class EntityDataSetting<D extends SettingGroup> extends RegistryDataSetting<EntityType<?>, D> {
   public EntityDataSetting(String name, Supplier<D> factory) {
      this(name, factory, List.of());
   }

   public EntityDataSetting(String name, Supplier<D> factory, Collection<EntityType<?>> defaults) {
      super(name, RegistryKind.ENTITY, factory, defaults);
   }

   public EntityDataSetting(String name, Function<EntityType<?>, D> factory, Collection<EntityType<?>> defaults) {
      super(name, RegistryKind.ENTITY, factory, defaults);
   }
}
