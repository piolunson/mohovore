package dev.leonetic.plugin;

public interface HomovorePlugin {
   void onInitialize(PluginContext var1);

   default void onShutdown() {
   }
}
