package dev.piolunson.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.piolunson.Homovore;
import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.features.Feature;
import dev.piolunson.features.commands.ModuleCommand;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.ClickGuiModule;
import dev.piolunson.features.modules.client.HudClientModule;
import dev.piolunson.features.modules.client.NotificationsModule;
import dev.piolunson.features.modules.client.TargetsModule;
import dev.piolunson.features.modules.combat.AutoAnchorModule;
import dev.piolunson.features.modules.combat.AutoCrystalModule;
import dev.piolunson.features.modules.combat.AutoLogModule;
import dev.piolunson.features.modules.combat.AutoMineModule;
import dev.piolunson.features.modules.combat.AutoSwordModule;
import dev.piolunson.features.modules.combat.AutoTotemModule;
import dev.piolunson.features.modules.combat.AutoTrapModule;
import dev.piolunson.features.modules.combat.KeyPotionModule;
import dev.piolunson.features.modules.combat.PearlBlockerModule;
import dev.piolunson.features.modules.combat.PhaseModule;
import dev.piolunson.features.modules.combat.SurroundModule;
import dev.piolunson.features.modules.combat.SwordGapModule;
import dev.piolunson.features.modules.funny.AutoHorseModule;
import dev.piolunson.features.modules.funny.DiscordRPCModule;
import dev.piolunson.features.modules.funny.EndermanLookModule;
import dev.piolunson.features.modules.funny.MoFuckerModule;
import dev.piolunson.features.modules.funny.TrickshotModule;
import dev.piolunson.features.modules.movement.ElytraAssistModule;
import dev.piolunson.features.modules.movement.ElytraDashModule;
import dev.piolunson.features.modules.movement.ElytraFlyModule;
import dev.piolunson.features.modules.movement.NoSlowModule;
import dev.piolunson.features.modules.movement.SprintModule;
import dev.piolunson.features.modules.movement.VelocityModule;
import dev.piolunson.features.modules.player.AutoXPModule;
import dev.piolunson.features.modules.player.FastuseModule;
import dev.piolunson.features.modules.player.InstantRekitModule;
import dev.piolunson.features.modules.player.MiddleClickExtraModule;
import dev.piolunson.features.modules.player.NoRotateModule;
import dev.piolunson.features.modules.player.ReplenishModule;
import dev.piolunson.features.modules.render.BlockHighlightModule;
import dev.piolunson.features.modules.render.BreadcrumbsModule;
import dev.piolunson.features.modules.render.BreakIndicatorsModule;
import dev.piolunson.features.modules.render.CrystalHandModule;
import dev.piolunson.features.modules.render.FullbrightModule;
import dev.piolunson.features.modules.render.LogoutSpotsModule;
import dev.piolunson.features.modules.render.NametagsModule;
import dev.piolunson.features.modules.render.NoRenderModule;
import dev.piolunson.features.modules.render.PopEffectsModule;
import dev.piolunson.features.modules.render.QuadSupremeModule;
import dev.piolunson.features.modules.render.SeeThroughModule;
import dev.piolunson.features.modules.render.ShadersModule;
import dev.piolunson.features.modules.render.ShulkerPreviewModule;
import dev.piolunson.features.modules.render.SkyboxModule;
import dev.piolunson.features.modules.render.TablistModule;
import dev.piolunson.features.modules.render.ViewModelModule;
import dev.piolunson.features.modules.world.AutoPortalModule;
import dev.piolunson.features.modules.world.BomberModule;
import dev.piolunson.features.modules.world.FastPortalModule;
import dev.piolunson.features.modules.world.NukerModule;
import dev.piolunson.features.modules.world.ScaffoldModule;
import dev.piolunson.features.modules.world.SpeedMineModule;
import dev.piolunson.util.traits.Jsonable;
import dev.piolunson.util.traits.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleManager implements Jsonable, Util {
   private static final Logger LOGGER = LoggerFactory.getLogger("ModuleManager");
   private final Map<Class<? extends Module>, Module> fastRegistry = new HashMap<>();
   private final List<Module> modules = new ArrayList<>();

   public void init() {
      this.register(new HudClientModule());
      this.register(new ClickGuiModule());
      this.register(new NotificationsModule());
      this.register(new TargetsModule());
      this.register(new AutoTrapModule());
      this.register(new AutoTotemModule());
      this.register(new SwordGapModule());
      this.register(new AutoLogModule());
      this.register(new AutoSwordModule());
      this.register(new AutoCrystalModule());
      this.register(new AutoAnchorModule());
      this.register(new AutoMineModule());
      this.register(new AutoXPModule());
      this.register(new VelocityModule());
      this.register(new SprintModule());
      this.register(new ElytraAssistModule());
      this.register(new ElytraFlyModule());
      this.register(new ElytraDashModule());
      this.register(new NoSlowModule());
      this.register(new AutoPortalModule());
      this.register(new FastPortalModule());
      this.register(new FastuseModule());
      this.register(new BlockHighlightModule());
      this.register(new BreadcrumbsModule());
      this.register(new BreakIndicatorsModule());
      this.register(new AutoHorseModule());
      this.register(new DiscordRPCModule());
      this.register(new EndermanLookModule());
      this.register(new CrystalHandModule());
      this.register(new FullbrightModule());
      this.register(new NoRenderModule());
      this.register(new ShadersModule());
      this.register(new SkyboxModule());
      this.register(new SeeThroughModule());
      this.register(new NametagsModule());
      this.register(new TablistModule());
      this.register(new LogoutSpotsModule());
      this.register(new PopEffectsModule());
      this.register(new ShulkerPreviewModule());
      this.register(new QuadSupremeModule());
      this.register(new ViewModelModule());
      this.register(new MiddleClickExtraModule());
      this.register(new KeyPotionModule());
      this.register(new SurroundModule());
      this.register(new PearlBlockerModule());
      this.register(new PhaseModule());
      this.register(new TrickshotModule());
      this.register(new MoFuckerModule());
      this.register(new BomberModule());
      this.register(new SpeedMineModule());
      this.register(new NukerModule());
      this.register(new ScaffoldModule());
      this.register(new ReplenishModule());
      this.register(new InstantRekitModule());
      this.register(new NoRotateModule());
      LOGGER.info("Registered {} modules", this.modules.size());

      for (Module module : this.modules) {
         Homovore.commandManager.register(new ModuleCommand(module));
      }

      Homovore.configManager.addConfig(this);
   }

   public void register(Module module) {
      this.getModules().add(module);
      this.fastRegistry.put((Class<? extends Module>)module.getClass(), module);
   }

   public List<Module> getModules() {
      return this.modules;
   }

   public Stream<Module> stream() {
      return this.getModules().stream();
   }

   public <T extends Module> T getModuleByClass(Class<T> clazz) {
      return (T)this.fastRegistry.get(clazz);
   }

   public Module getModuleByName(String name) {
      return this.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
   }

   public Module getModuleByDisplayName(String display) {
      return this.stream().filter(m -> m.getDisplayName().equalsIgnoreCase(display)).findFirst().orElse(null);
   }

   public List<Module> getModulesByCategory(Module.Category category) {
      return this.stream().filter(m -> m.getCategory() == category).toList();
   }

   public List<Module.Category> getCategories() {
      return Arrays.asList(Module.Category.values());
   }

   public void onLoad() {
      this.getModules().forEach(Module::onLoad);
   }

   public void onTick() {
      this.stream().filter(Feature::isEnabled).forEach(Module::onTick);
      this.stream().filter(m -> m.isEnabled() && m.getBindMode() == Module.BindMode.HOLD && !m.getBind().isDown()).toList().forEach(Module::disable);
   }

   public void onRender2D(Render2DEvent event) {
      this.stream().filter(Feature::isEnabled).forEach(module -> module.onRender2D(event));
   }

   public void onRender3D(Render3DEvent event) {
      this.stream().filter(Feature::isEnabled).forEach(module -> module.onRender3D(event));
   }

   public void onUnload() {
      this.getModules().forEach(EVENT_BUS::unregister);
      this.getModules().forEach(Module::onUnload);
   }

   public void onKeyPressed(int key) {
      if (key > 0 && mc.currentScreen == null) {
         this.stream().filter(module -> module.getBind().getKey() == key).forEach(module -> {
            if (module.getBindMode() == Module.BindMode.HOLD) {
               if (!module.isEnabled()) {
                  module.enable();
               }
            } else {
               module.toggle();
            }
         });
      }
   }

   public void onKeyReleased(int key) {
      if (key > 0) {
         this.stream()
            .filter(module -> module.getBind().getKey() == key && module.getBindMode() == Module.BindMode.HOLD && module.isEnabled())
            .forEach(Module::disable);
      }
   }

   public void onMousePressed(int button) {
      if (mc.currentScreen == null) {
         int key = 1000 + button;
         this.stream().filter(module -> module.getBind().getKey() == key).forEach(module -> {
            if (module.getBindMode() == Module.BindMode.HOLD) {
               if (!module.isEnabled()) {
                  module.enable();
               }
            } else {
               module.toggle();
            }
         });
      }
   }

   public void onMouseReleased(int button) {
      int key = 1000 + button;
      this.stream()
         .filter(module -> module.getBind().getKey() == key && module.getBindMode() == Module.BindMode.HOLD && module.isEnabled())
         .forEach(Module::disable);
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Module module : this.getModules()) {
         object.add(module.getName(), module.toJson());
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      for (Module module : this.getModules()) {
         try {
            module.fromJson(element.getAsJsonObject().get(module.getName()));
         } catch (Throwable e) {
            LOGGER.error("Failed to load module {}", module.getName(), e);
         }
      }
   }

   @Override
   public String getFileName() {
      return "modules.json";
   }
}
