package dev.leonetic.util.traits;

import dev.leonetic.event.system.EventBus;
import net.minecraft.client.MinecraftClient;

public interface Util {
   MinecraftClient mc = MinecraftClient.getInstance();
   EventBus EVENT_BUS = new EventBus();
}
