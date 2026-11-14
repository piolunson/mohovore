package dev.leonetic.v2.plugin;

import com.google.gson.Gson;
import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.config.ConfigManager;
import dev.leonetic.v2.feature.command.Command;
import dev.leonetic.v2.feature.command.CommandManager;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.module.ModuleManager;
import dev.leonetic.v2.gui.CategoryIcon;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

public final class PluginManager implements AutoCloseable {
   public static final String METADATA_FILE = "homovore.plugin.json";
   public static final String ENTRYPOINT = "homovore";
   private static final String DEFAULT_COLOR = "#7C4DFF";
   private static final int MAX_ICON_BYTES = 1048576;
   private static final Gson GSON = new Gson();
   private final ModuleManager moduleManager;
   private final ConfigManager configManager;
   private final Path pluginDirectory;
   private final List<PluginManager.LoadedPlugin> loaded = new ArrayList<>();
   private final Set<String> ids = new HashSet<>();

   public PluginManager(ModuleManager moduleManager, ConfigManager configManager) {
      this.moduleManager = moduleManager;
      this.configManager = configManager;
      this.pluginDirectory = FabricLoader.getInstance().getGameDir().resolve("homovore/plugins");
   }

   public void loadAll() {
      this.loadMods();

      try {
         Files.createDirectories(this.pluginDirectory);

         try (Stream<Path> files = Files.list(this.pluginDirectory)) {
            files.filter(x$0 -> Files.isRegularFile(x$0))
               .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
               .sorted(Comparator.comparing(path -> path.getFileName().toString()))
               .forEach(this::loadJar);
         }
      } catch (IOException exception) {
         Homovore2.LOGGER.error("Could not scan Homovore plugin directory {}", this.pluginDirectory, exception);
      }

      Homovore2.LOGGER.info("Loaded {} Homovore plugin(s)", this.loaded.size());
   }

   private void loadMods() {
      for (EntrypointContainer<HomovorePlugin> container : FabricLoader.getInstance().getEntrypointContainers("homovore", HomovorePlugin.class)) {
         ModContainer mod = container.getProvider();
         String source = mod.getMetadata().getId() + " (mod)";

         try {
            PluginMetadata metadata = modMetadata(mod);
            this.install(source, metadata, container::getEntrypoint, (path, limit) -> {
               Optional<Path> file = mod.findPath(path);
               if (!file.isEmpty() && Files.isRegularFile(file.get())) {
                  try (InputStream stream = Files.newInputStream(file.get())) {
                     return stream.readNBytes(limit + 1);
                  }
               } else {
                  return null;
               }
            }, null);
         } catch (Throwable throwable) {
            Homovore2.LOGGER.error("Failed to load Homovore plugin {}", source, throwable);
         }
      }
   }

   private static PluginMetadata modMetadata(ModContainer mod) throws IOException {
      ModMetadata meta = mod.getMetadata();
      Optional<Path> file = mod.findPath("homovore.plugin.json");
      if (file.isPresent() && Files.isRegularFile(file.get())) {
         try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.get()), StandardCharsets.UTF_8)) {
            PluginMetadata declared = (PluginMetadata)GSON.fromJson(reader, PluginMetadata.class);
            if (declared != null) {
               return new PluginMetadata(
                  orElse(declared.id(), meta.getId()),
                  orElse(declared.name(), meta.getName()),
                  meta.getVersion().getFriendlyString(),
                  null,
                  declared.category()
               );
            }
         }
      }

      return new PluginMetadata(
         meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), null, new PluginMetadata.CategoryMetadata(meta.getName(), "#7C4DFF", null)
      );
   }

   private static String orElse(String value, String fallback) {
      return value != null && !value.isBlank() ? value : fallback;
   }

   private void loadJar(Path path) {
      URLClassLoader loader = null;
      String source = path.getFileName().toString();

      try (JarFile jar = new JarFile(path.toFile())) {
         PluginMetadata metadata = readMetadata(jar);
         require(metadata == null ? null : metadata.entrypoint(), "entrypoint");
         URLClassLoader jarLoader = new URLClassLoader(new URL[]{path.toUri().toURL()}, HomovorePlugin.class.getClassLoader());
         loader = jarLoader;
         this.install(source, metadata, () -> {
            Class<?> entrypoint = Class.forName(metadata.entrypoint(), true, jarLoader);
            if (!HomovorePlugin.class.isAssignableFrom(entrypoint)) {
               throw new IllegalArgumentException(metadata.entrypoint() + " does not implement HomovorePlugin");
            } else {
               return (HomovorePlugin)entrypoint.getDeclaredConstructor().newInstance();
            }
         }, (iconPath, limit) -> {
            JarEntry entry = jar.getJarEntry(iconPath);
            if (entry == null) {
               return null;
            }

            try (InputStream stream = jar.getInputStream(entry)) {
               return stream.readNBytes(limit + 1);
            }
         }, jarLoader);
      } catch (Throwable throwable) {
         Homovore2.LOGGER.error("Failed to load Homovore plugin {}", source, throwable);
         if (loader != null) {
            try {
               loader.close();
            } catch (IOException var7) {
            }
         }
      }
   }

   private void install(String source, PluginMetadata metadata, PluginManager.Factory factory, PluginManager.Resources resources, URLClassLoader loader) throws Exception {
      validate(metadata);
      String normalizedId = metadata.id().toLowerCase(Locale.ROOT);
      if (!this.ids.add(normalizedId)) {
         throw new IllegalArgumentException("Duplicate plugin id '" + metadata.id() + "'");
      }

      Color color = parseColor(metadata.category().color());
      Category category = Category.registerPlugin(metadata.id(), metadata.category().name(), color);

      try {
         HomovorePlugin plugin = factory.create();
         PluginContext context = new PluginContext(metadata, category);
         plugin.onInitialize(context);

         try {
            context.commit(this.moduleManager, this.configManager);
         } catch (RuntimeException | Error failure) {
            try {
               plugin.onShutdown();
            } catch (Throwable shutdown) {
               failure.addSuppressed(shutdown);
            }

            throw failure;
         }

         loadIcon(resources, metadata, category);
         this.loaded.add(new PluginManager.LoadedPlugin(metadata, plugin, context, loader));
         Homovore2.LOGGER.info("Loaded Homovore plugin {} {} from {}", new Object[]{metadata.name(), metadata.version(), source});
      } catch (Throwable throwable) {
         Category.unregisterPlugin(category);
         throw throwable;
      }
   }

   private static PluginMetadata readMetadata(JarFile jar) throws IOException {
      JarEntry entry = jar.getJarEntry("homovore.plugin.json");
      if (entry == null) {
         throw new IllegalArgumentException("Missing homovore.plugin.json");
      }

      try (
         InputStream stream = jar.getInputStream(entry);
         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
      ) {
         return (PluginMetadata)GSON.fromJson(reader, PluginMetadata.class);
      }
   }

   private static void validate(PluginMetadata metadata) {
      if (metadata == null) {
         throw new IllegalArgumentException("Empty plugin metadata");
      }

      require(metadata.id(), "id");
      if (!metadata.id().matches("[a-z][a-z0-9_.-]{0,63}")) {
         throw new IllegalArgumentException("Plugin id must match [a-z][a-z0-9_.-]{0,63}");
      }

      require(metadata.name(), "name");
      require(metadata.version(), "version");
      if (metadata.category() == null) {
         throw new IllegalArgumentException("Missing category object");
      }

      require(metadata.category().name(), "category.name");
      require(metadata.category().color(), "category.color");
   }

   private static void require(String value, String field) {
      if (value == null || value.isBlank()) {
         throw new IllegalArgumentException("Missing " + field);
      }
   }

   private static Color parseColor(String raw) {
      String hex = raw.startsWith("#") ? raw.substring(1) : raw;
      if (!hex.matches("[0-9a-fA-F]{6}")) {
         throw new IllegalArgumentException("category.color must be a six-digit RGB hex color");
      } else {
         return new Color(Integer.parseInt(hex, 16));
      }
   }

   private static void loadIcon(PluginManager.Resources resources, PluginMetadata metadata, Category category) {
      String iconPath = metadata.category().icon();
      if (iconPath != null && !iconPath.isBlank()) {
         try {
            byte[] bytes = resources.read(iconPath, 1048576);
            if (bytes == null) {
               Homovore2.LOGGER.warn("Plugin {} icon '{}' was not found; using puzzle piece", metadata.id(), iconPath);
               return;
            }

            if (bytes.length > 1048576) {
               throw new IOException("icon is larger than 1 MiB");
            }

            CategoryIcon.registerPlugin(category, metadata.id(), bytes);
         } catch (Throwable throwable) {
            Homovore2.LOGGER.warn("Could not load icon for plugin {}; using puzzle piece", metadata.id(), throwable);
         }
      }
   }

   public void registerCommands(CommandManager commandManager) {
      for (PluginManager.LoadedPlugin plugin : this.loaded) {
         for (Command command : plugin.context().stagedCommands()) {
            commandManager.register(command);
         }
      }
   }

   public List<PluginMetadata> getPlugins() {
      return this.loaded.stream().map(PluginManager.LoadedPlugin::metadata).toList();
   }

   public Path getPluginDirectory() {
      return this.pluginDirectory;
   }

   @Override
   public void close() {
      for (int i = this.loaded.size() - 1; i >= 0; i--) {
         PluginManager.LoadedPlugin loadedPlugin = this.loaded.get(i);

         for (Module module : loadedPlugin.context().stagedModules()) {
            if (module.isEnabled()) {
               Homovore2Client.EVENT_BUS.unregister(module);

               try {
                  module.onDisable();
               } catch (Throwable throwable) {
                  Homovore2.LOGGER.error("Plugin module {} failed during shutdown", module.getName(), throwable);
               }
            }
         }

         loadedPlugin.context().unregisterListeners();

         try {
            loadedPlugin.plugin().onShutdown();
         } catch (Throwable throwable) {
            Homovore2.LOGGER.error("Plugin {} failed during shutdown", loadedPlugin.metadata().id(), throwable);
         }

         if (loadedPlugin.loader() != null) {
            try {
               loadedPlugin.loader().close();
            } catch (IOException var7) {
            }
         }
      }

      CategoryIcon.closePluginTextures();
      this.loaded.clear();
   }

   @FunctionalInterface
   private interface Factory {
      HomovorePlugin create() throws Exception;
   }

   private record LoadedPlugin(PluginMetadata metadata, HomovorePlugin plugin, PluginContext context, URLClassLoader loader) {
   }

   @FunctionalInterface
   private interface Resources {
      byte[] read(String var1, int var2) throws IOException;
   }
}
