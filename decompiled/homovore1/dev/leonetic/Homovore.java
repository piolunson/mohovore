package dev.leonetic;

import dev.leonetic.features.GuiMove;
import dev.leonetic.features.modules.Module;
import dev.leonetic.manager.ColorManager;
import dev.leonetic.manager.CommandManager;
import dev.leonetic.manager.ConfigManager;
import dev.leonetic.manager.EnemyManager;
import dev.leonetic.manager.EventManager;
import dev.leonetic.manager.FriendManager;
import dev.leonetic.manager.ModuleManager;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.manager.PlayerInfoManager;
import dev.leonetic.manager.PositionManager;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.manager.SwapManager;
import dev.leonetic.manager.TPSCounterService;
import dev.leonetic.util.TextUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_3414;
import net.minecraft.class_7923;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Homovore implements ModInitializer, ClientModInitializer {
   public static float TIMER = 1.0F;
   public static final class_2960 AUTOHORSE_SOUND_ID = class_2960.method_60655("homovore", "autohorse");
   public static final class_3414 AUTOHORSE_SOUND = class_3414.method_47908(AUTOHORSE_SOUND_ID);
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
      class_2378.method_10230(class_7923.field_41172, AUTOHORSE_SOUND_ID, AUTOHORSE_SOUND);
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
