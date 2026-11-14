package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;

public class ClientEvent extends Event {
   private final ClientEvent.Type type;
   private final Module module;
   private Setting<?> setting;

   public ClientEvent(ClientEvent.Type type, Module module) {
      this.type = type;
      this.module = module;
   }

   public ClientEvent(Setting<?> setting) {
      this(ClientEvent.Type.SETTING_UPDATE, setting.getOwner());
      this.setting = setting;
   }

   public ClientEvent.Type getType() {
      return this.type;
   }

   public Module getModule() {
      return this.module;
   }

   public Setting<?> getSetting() {
      return this.setting;
   }

   public enum Type {
      TOGGLE_MODULE,
      SETTING_UPDATE;
   }
}
