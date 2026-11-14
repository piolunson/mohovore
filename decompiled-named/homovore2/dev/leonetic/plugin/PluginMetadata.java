package dev.leonetic.plugin;

public record PluginMetadata(String id, String name, String version, String entrypoint, PluginMetadata.CategoryMetadata category) {
   public record CategoryMetadata(String name, String color, String icon) {
   }
}
