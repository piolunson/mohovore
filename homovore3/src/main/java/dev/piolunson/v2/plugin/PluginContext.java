package dev.piolunson.v2.plugin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.config.ConfigManager;
import dev.piolunson.v2.event.system.EventBus;
import dev.piolunson.v2.feature.command.Command;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.module.ModuleManager;
import dev.piolunson.v2.manager.EnemyManager;
import dev.piolunson.v2.manager.FriendManager;
import dev.piolunson.v2.manager.PlacementManager;
import dev.piolunson.v2.manager.RotationManager;
import dev.piolunson.v2.manager.TotemPopManager;
import dev.piolunson.v2.manager.TpsManager;
import dev.piolunson.v2.util.Jsonable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PluginContext {
   private final PluginMetadata metadata;
   private final Category category;
   private final List<Module> modules = new ArrayList<>();
   private final List<Command> commands = new ArrayList<>();
   private final List<Jsonable> configs = new ArrayList<>();
   private final List<Object> listeners = new ArrayList<>();
   private boolean initializing = true;

   PluginContext(PluginMetadata metadata, Category category) {
      this.metadata = metadata;
      this.category = category;
   }

   public PluginMetadata metadata() {
      return this.metadata;
   }

   public Category category() {
      return this.category;
   }

   public <T extends Module> T registerModule(T module) {
      this.ensureInitializing();
      Objects.requireNonNull(module, "module");
      if (module.getCategory() != this.category) {
         throw new IllegalArgumentException("Plugin module '" + module.getName() + "' must use context.category()");
      }

      this.modules.add(module);
      return module;
   }

   public <T extends Command> T registerCommand(T command) {
      this.ensureInitializing();
      this.commands.add(Objects.requireNonNull(command, "command"));
      return command;
   }

   public <T extends Jsonable> T registerConfig(T config) {
      this.ensureInitializing();
      this.configs.add(Objects.requireNonNull(config, "config"));
      return config;
   }

   public <T> T registerListener(T listener) {
      this.ensureInitializing();
      this.listeners.add(Objects.requireNonNull(listener, "listener"));
      return listener;
   }

   public ModuleManager modules() {
      return Homovore2Client.modules();
   }

   public ConfigManager config() {
      return Homovore2Client.config();
   }

   public EventBus events() {
      return Homovore2Client.EVENT_BUS;
   }

   public RotationManager rotations() {
      return Homovore2Client.rotations();
   }

   public PlacementManager placements() {
      return Homovore2Client.placements();
   }

   public TotemPopManager totemPops() {
      return Homovore2Client.totemPops();
   }

   public TpsManager tps() {
      return Homovore2Client.tps();
   }

   public FriendManager friends() {
      return Homovore2Client.friends();
   }

   public EnemyManager enemies() {
      return Homovore2Client.enemies();
   }

   void commit(ModuleManager moduleManager, ConfigManager configManager) {
      try {
         for (Object listener : this.listeners) {
            Homovore2Client.EVENT_BUS.register(listener);
         }

         moduleManager.registerAll(this.modules);
      } catch (RuntimeException | Error failure) {
         this.unregisterListeners();
         throw failure;
      }

      for (Jsonable config : this.configs) {
         configManager.addConfig(config);
      }

      this.initializing = false;
   }

   void unregisterListeners() {
      for (Object listener : this.listeners) {
         Homovore2Client.EVENT_BUS.unregister(listener);
      }
   }

   List<Module> stagedModules() {
      return List.copyOf(this.modules);
   }

   List<Command> stagedCommands() {
      return List.copyOf(this.commands);
   }

   private void ensureInitializing() {
      if (!this.initializing) {
         throw new IllegalStateException("Plugin registrations are only accepted during onInitialize");
      }
   }
}
