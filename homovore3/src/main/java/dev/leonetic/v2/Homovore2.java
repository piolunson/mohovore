package dev.leonetic.v2;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
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

   public static Identifier id(String path) {
      return Identifier.of("homovore-2", path);
   }
}
