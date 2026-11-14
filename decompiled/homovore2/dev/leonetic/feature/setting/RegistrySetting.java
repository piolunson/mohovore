package dev.leonetic.feature.setting;

import java.util.Collection;

public class RegistrySetting<T> extends MultiListSetting<T> {
   private final RegistryKind<T> kind;

   public RegistrySetting(String name, RegistryKind<T> kind, Collection<T> defaults) {
      super(name, defaults, kind::getEntries, kind::displayName, kind::getKey, kind::byKey);
      this.kind = kind;
   }

   public RegistryKind<T> getKind() {
      return this.kind;
   }

   @Override
   public String getType() {
      return "Registry";
   }
}
