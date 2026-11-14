package dev.leonetic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2960;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Homovore2 implements ModInitializer {
   public static final String MOD_ID = "homovore-2";
   public static final Logger LOGGER = LoggerFactory.getLogger("homovore-2");

   public void onInitialize() {
      LOGGER.info("Hello Fabric world!");
   }

   public static String version() {
      return FabricLoader.getInstance().getModContainer("homovore-2").map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
   }

   public static class_2960 id(String path) {
      return class_2960.method_60655("homovore-2", path);
   }
}
