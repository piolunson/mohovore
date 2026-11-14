package dev.leonetic.v2.plugin;

public interface HomovorePlugin {
   void onInitialize(PluginContext var1);

   default void onShutdown() {
   }
}
