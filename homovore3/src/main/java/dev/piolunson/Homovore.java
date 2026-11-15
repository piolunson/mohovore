package dev.piolunson;

import dev.piolunson.features.GuiMove;
import dev.piolunson.features.modules.Module;
import dev.piolunson.manager.ColorManager;
import dev.piolunson.manager.CommandManager;
import dev.piolunson.manager.ConfigManager;
import dev.piolunson.manager.EnemyManager;
import dev.piolunson.manager.EventManager;
import dev.piolunson.manager.FriendManager;
import dev.piolunson.manager.ModuleManager;
import dev.piolunson.manager.PlacementManager;
import dev.piolunson.manager.PlayerInfoManager;
import dev.piolunson.manager.PositionManager;
import dev.piolunson.manager.RotationManager;
import dev.piolunson.manager.SwapManager;
import dev.piolunson.manager.TPSCounterService;
import dev.piolunson.util.TextUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Homovore implements ModInitializer, ClientModInitializer {
   public static float TIMER = 1.0F;
   public static final Identifier AUTOHORSE_SOUND_ID = Identifier.of("homovore", "autohorse");
   public static final SoundEvent AUTOHORSE_SOUND = SoundEvent.of(AUTOHORSE_SOUND_ID);
   public static final Logger LOGGER = LogManager.getLogger("Homovore");
   public static ColorManager colorManager;
   public static PositionManager positionManager;
   public static EventManager eventManager;
   public static CommandManager commandManager;
   public static FriendManager friendManager;
   public static EnemyManager enemyManager;
   public static PlayerInfoManager playerInfoManager;
   public static ModuleManager moduleManager;
   public static ConfigManager configManager;
   public static PlacementManager placementManager;
   public static RotationManager rotationManager;
   public static SwapManager swapManager;
   public static TPSCounterService tpsCounterService;
   public static GuiMove guiMove;

   public void onInitialize() {
      LOGGER.info("Pre-initializing {} v{}", "Homovore", "1.2.1");
      Registry.register(Registries.SOUND_EVENT, AUTOHORSE_SOUND_ID, AUTOHORSE_SOUND);
      configManager = new ConfigManager();
      eventManager = new EventManager();
      positionManager = new PositionManager();
      friendManager = new FriendManager();
      enemyManager = new EnemyManager();
      playerInfoManager = new PlayerInfoManager();
      colorManager = new ColorManager();
      commandManager = new CommandManager();
      moduleManager = new ModuleManager();
      placementManager = new PlacementManager();
      rotationManager = new RotationManager();
      swapManager = new SwapManager();
      tpsCounterService = new TPSCounterService();
      guiMove = new GuiMove();
      TextUtil.init();
   }

   public void onInitializeClient() {
      LOGGER.info("Initializing {}", "Homovore");
      long startTime = System.nanoTime();
      eventManager.init();
      rotationManager.init();
      swapManager.init();
      commandManager.init();
      moduleManager.init();
      friendManager.init();
      enemyManager.init();
      playerInfoManager.init();
      tpsCounterService.init();
      guiMove.init();
      configManager.load();
      if (!commandManager.isFunnyVisible()) {
         moduleManager.stream().filter(m -> m.getCategory() == Module.Category.FUNNY).filter(m -> m.isEnabled()).forEach(Module::disable);
      }

      moduleManager.onLoad();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> configManager.save()));
      long endTime = System.nanoTime();
      LOGGER.info("Initialized {} in {}ms", "Homovore", (endTime - startTime) / 1000000.0);
   }
}
