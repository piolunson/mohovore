package dev.leonetic;

import dev.leonetic.config.ConfigManager;
import dev.leonetic.event.impl.TickEvent;
import dev.leonetic.event.system.EventBus;
import dev.leonetic.feature.command.CommandManager;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.module.impl.PacketLimitsModule;
import dev.leonetic.gui.ClickGuiConfig;
import dev.leonetic.manager.BreakManager;
import dev.leonetic.manager.EnemyManager;
import dev.leonetic.manager.FriendManager;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.manager.PotionEffectManager;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.manager.TotemPopManager;
import dev.leonetic.manager.TpsManager;
import dev.leonetic.plugin.PluginManager;
import dev.leonetic.render.NotificationTray;
import dev.leonetic.util.ChatUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimitSettings;
import dev.leonetic.util.world.CrystalWorldSnapshot;
import dev.leonetic.util.world.PlacementWorldSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.class_310;

@Environment(EnvType.CLIENT)
public class Homovore2Client implements ClientModInitializer {
   public static final EventBus EVENT_BUS = new EventBus();
   private static ModuleManager moduleManager;
   private static ConfigManager configManager;
   private static CommandManager commandManager;
   private static RotationManager rotationManager;
   private static PlacementManager placementManager;
   private static TotemPopManager totemPopManager;
   private static PotionEffectManager potionEffectManager;
   private static BreakManager breakManager;
   private static TpsManager tpsManager;
   private static FriendManager friendManager;
   private static EnemyManager enemyManager;
   private static PluginManager pluginManager;

   public static ModuleManager modules() {
      return moduleManager;
   }

   public static ConfigManager config() {
      return configManager;
   }

   public static CommandManager commands() {
      return commandManager;
   }

   public static RotationManager rotations() {
      return rotationManager;
   }

   public static PlacementManager placements() {
      return placementManager;
   }

   public static TotemPopManager totemPops() {
      return totemPopManager;
   }

   public static PotionEffectManager potions() {
      return potionEffectManager;
   }

   public static BreakManager breaks() {
      return breakManager;
   }

   public static TpsManager tps() {
      return tpsManager;
   }

   public static FriendManager friends() {
      return friendManager;
   }

   public static EnemyManager enemies() {
      return enemyManager;
   }

   public static PluginManager plugins() {
      return pluginManager;
   }

   public void onInitializeClient() {
      rotationManager = new RotationManager();
      placementManager = new PlacementManager();
      totemPopManager = new TotemPopManager();
      potionEffectManager = new PotionEffectManager();
      breakManager = new BreakManager();
      tpsManager = new TpsManager();
      friendManager = new FriendManager();
      enemyManager = new EnemyManager();
      EVENT_BUS.register(totemPopManager);
      EVENT_BUS.register(potionEffectManager);
      EVENT_BUS.register(breakManager);
      EVENT_BUS.register(tpsManager);
      EVENT_BUS.register(placementManager);
      PacketLimitSettings.bindTps(() -> tpsManager.average());
      moduleManager = new ModuleManager();
      configManager = new ConfigManager();
      pluginManager = new PluginManager(moduleManager, configManager);
      pluginManager.loadAll();
      commandManager = new CommandManager();
      pluginManager.registerCommands(commandManager);
      configManager.addConfig(moduleManager);
      configManager.addConfig(new ClickGuiConfig());
      configManager.addConfig(friendManager);
      configManager.addConfig(enemyManager);
      configManager.load();
      ClientPlayConnectionEvents.JOIN.register((Join)(listener, sender, client) -> {
         NetworkThreadExecutor.bind(listener);
         InventoryUtil.capture(client);
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(listener, client) -> {
         NetworkThreadExecutor.unbind(listener);
         placementManager.clearStatus();
         InventoryUtil.clear();
         CrystalWorldSnapshot.clear();
         PlacementWorldSnapshot.clear();
         rotationManager.reset();
         totemPopManager.reset();
         potionEffectManager.reset();
         breakManager.reset();
         tpsManager.reset();
         NotificationTray.clear();
         ChatUtil.clearPersistent();
      });
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         NetworkThreadExecutor.syncClientSelection(client);
         rotationManager.applySilentRestoration();
      });
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         configManager.save();
         pluginManager.close();
      });
      Runtime.getRuntime().addShutdownHook(new Thread(() -> configManager.save()));
      Homovore2.LOGGER
         .info(
            "Homovore client initialised with {} modules, {} commands and {} plugins",
            new Object[]{moduleManager.getModules().size(), commandManager.getCommands().size(), pluginManager.getPlugins().size()}
         );
   }

   public static void beforeClientTickEnd(class_310 client) {
      PacketLimitsModule.get().publish();
      InventoryUtil.capture(client);
      CrystalWorldSnapshot.capture(client);
      PlacementWorldSnapshot.capture(client);
      if (NetworkThreadExecutor.isConnected()) {
         NetworkThreadExecutor.beginTick();
         rotationManager.beginTick();
      }

      moduleManager.pollBinds();
      EVENT_BUS.post(new TickEvent.Pre());
      moduleManager.onTick();
      EVENT_BUS.post(new TickEvent.Post());
   }
}
