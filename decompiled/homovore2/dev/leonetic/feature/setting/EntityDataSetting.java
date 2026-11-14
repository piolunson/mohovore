package dev.leonetic.feature.setting;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.class_1299;

public class EntityDataSetting<D extends SettingGroup> extends RegistryDataSetting<class_1299<?>, D> {
   public EntityDataSetting(String name, Supplier<D> factory) {
      this(name, factory, List.of());
   }

   public EntityDataSetting(String name, Supplier<D> factory, Collection<class_1299<?>> defaults) {
      super(name, RegistryKind.ENTITY, factory, defaults);
   }

   public EntityDataSetting(String name, Function<class_1299<?>, D> factory, Collection<class_1299<?>> defaults) {
      super(name, RegistryKind.ENTITY, factory, defaults);
   }
}
