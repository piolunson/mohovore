package dev.leonetic.feature.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.leonetic.config.DefaultConfig;
import dev.leonetic.feature.module.impl.AirPlaceModule;
import dev.leonetic.feature.module.impl.AnchorPlacerModule;
import dev.leonetic.feature.module.impl.AntiAfkModule;
import dev.leonetic.feature.module.impl.AutoEatModule;
import dev.leonetic.feature.module.impl.AutoElytraModule;
import dev.leonetic.feature.module.impl.AutoLogModule;
import dev.leonetic.feature.module.impl.AutoMineModule;
import dev.leonetic.feature.module.impl.AutoTotemModule;
import dev.leonetic.feature.module.impl.AutoTrapModule;
import dev.leonetic.feature.module.impl.AutoXpModule;
import dev.leonetic.feature.module.impl.BasePlaceModule;
import dev.leonetic.feature.module.impl.BetterCamModule;
import dev.leonetic.feature.module.impl.BounceModule;
import dev.leonetic.feature.module.impl.BreakIndicatorsModule;
import dev.leonetic.feature.module.impl.ChamsModule;
import dev.leonetic.feature.module.impl.ClickGuiModule;
import dev.leonetic.feature.module.impl.CrystalPlacerModule;
import dev.leonetic.feature.module.impl.CustomSoundsModule;
import dev.leonetic.feature.module.impl.DoubleMineModule;
import dev.leonetic.feature.module.impl.FallTrapModule;
import dev.leonetic.feature.module.impl.FastUseModule;
import dev.leonetic.feature.module.impl.FixesModule;
import dev.leonetic.feature.module.impl.FreecamModule;
import dev.leonetic.feature.module.impl.FullbrightModule;
import dev.leonetic.feature.module.impl.HotbarRefillModule;
import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.module.impl.InstantRekitModule;
import dev.leonetic.feature.module.impl.KeyActionModule;
import dev.leonetic.feature.module.impl.KillAuraModule;
import dev.leonetic.feature.module.impl.LogoutSpotsModule;
import dev.leonetic.feature.module.impl.MiddleClickModule;
import dev.leonetic.feature.module.impl.NametagsModule;
import dev.leonetic.feature.module.impl.NoClimbModule;
import dev.leonetic.feature.module.impl.NoJumpDelayModule;
import dev.leonetic.feature.module.impl.NoRenderModule;
import dev.leonetic.feature.module.impl.NoRotateModule;
import dev.leonetic.feature.module.impl.NotificationsModule;
import dev.leonetic.feature.module.impl.PacketLimitsModule;
import dev.leonetic.feature.module.impl.PearlBlockerModule;
import dev.leonetic.feature.module.impl.PhaseModule;
import dev.leonetic.feature.module.impl.PistonCrystalModule;
import dev.leonetic.feature.module.impl.PopChamsModule;
import dev.leonetic.feature.module.impl.RegearBlockerModule;
import dev.leonetic.feature.module.impl.ScaffoldModule;
import dev.leonetic.feature.module.impl.SelfTrapModule;
import dev.leonetic.feature.module.impl.Server;
import dev.leonetic.feature.module.impl.ShulkerPreviewModule;
import dev.leonetic.feature.module.impl.SoundBlockerModule;
import dev.leonetic.feature.module.impl.SprintModule;
import dev.leonetic.feature.module.impl.SwordGapModule;
import dev.leonetic.feature.module.impl.TablistModule;
import dev.leonetic.feature.module.impl.TracersModule;
import dev.leonetic.feature.module.impl.TrailsModule;
import dev.leonetic.feature.module.impl.VelocityModule;
import dev.leonetic.feature.module.impl.ViewModelModule;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.Jsonable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_4184;

public final class ModuleManager implements Jsonable {
   private final List<Module> modules = new ArrayList<>();
   private final List<Module> tickOrder = new ArrayList<>();
   private final Set<Module> heldBinds = new HashSet<>();
   private static final Comparator<Module> TICK_ORDER = Comparator.comparingInt(Module::getTickPriority).reversed().thenComparing(Module::getName);
   public static final String PARTIAL_KEY = "__partial";

   public ModuleManager() {
      this.register(new AirPlaceModule());
      this.register(new AntiAfkModule());
      this.register(new AnchorPlacerModule());
      this.register(new AutoMineModule());
      this.register(new AutoEatModule());
      this.register(new AutoElytraModule());
      this.register(new AutoLogModule());
      this.register(new AutoTotemModule());
      this.register(new AutoTrapModule());
      this.register(new AutoXpModule());
      this.register(new BasePlaceModule());
      this.register(new BetterCamModule());
      this.register(new BounceModule());
      this.register(new BreakIndicatorsModule());
      this.register(new ClickGuiModule());
      this.register(new CrystalPlacerModule());
      this.register(new CustomSoundsModule());
      this.register(new DoubleMineModule());
      this.register(new FallTrapModule());
      this.register(new FastUseModule());
      this.register(new FixesModule());
      this.register(new FreecamModule());
      this.register(new FullbrightModule());
      this.register(new HotbarRefillModule());
      this.register(new HudModule());
      this.register(new InstantRekitModule());
      this.register(new KeyActionModule());
      this.register(new KillAuraModule());
      this.register(new LogoutSpotsModule());
      this.register(new MiddleClickModule());
      this.register(new NametagsModule());
      this.register(new NoClimbModule());
      this.register(new NoJumpDelayModule());
      this.register(new NoRenderModule());
      this.register(new NoRotateModule());
      this.register(new NotificationsModule());
      this.register(new PacketLimitsModule());
      this.register(new ScaffoldModule());
      this.register(new SelfTrapModule());
      this.register(new ShulkerPreviewModule());
      this.register(new ChamsModule());
      this.register(new PearlBlockerModule());
      this.register(new PistonCrystalModule());
      this.register(new RegearBlockerModule());
      this.register(new PhaseModule());
      this.register(new PopChamsModule());
      this.register(new SoundBlockerModule());
      this.register(new SprintModule());
      this.register(new SwordGapModule());
      this.register(new TracersModule());
      this.register(new TablistModule());
      this.register(new TrailsModule());
      this.register(new Server());
      this.register(new VelocityModule());
      this.register(new ViewModelModule());
      this.modules.sort(Comparator.comparing(Module::getName));
      this.tickOrder.sort(TICK_ORDER);
   }

   public void register(Module module) {
      this.registerAll(List.of(module));
   }

   public void registerAll(Collection<? extends Module> additions) {
      Set<String> names = new HashSet<>();

      for (Module existing : this.modules) {
         names.add(existing.getName().toLowerCase(Locale.ROOT));
      }

      for (Module module : additions) {
         String name = module.getName().toLowerCase(Locale.ROOT);
         if (!names.add(name)) {
            throw new IllegalArgumentException("A module named '" + module.getName() + "' is already registered");
         }
      }

      this.modules.addAll(additions);
      this.tickOrder.addAll(additions);
      this.modules.sort(Comparator.comparing(Module::getName));
      this.tickOrder.sort(TICK_ORDER);
   }

   public List<Module> getModules() {
      return this.modules;
   }

   public List<Module> byCategory(Category category) {
      return this.modules.stream().filter(module -> module.getCategory() == category).toList();
   }

   public <T extends Module> T get(Class<T> type) {
      for (Module module : this.modules) {
         if (type.isInstance(module)) {
            return (T)module;
         }
      }

      return null;
   }

   public void onTick() {
      for (Module module : this.tickOrder) {
         if (module.isEnabled()) {
            module.onTick();
         }
      }
   }

   public void onRender2D(class_332 graphics, float partialTick) {
      for (Module module : this.modules) {
         if (module.isEnabled()) {
            module.onRender2D(graphics, partialTick);
         }
      }
   }

   public void onRender3D(class_4184 camera, float partialTick) {
      for (Module module : this.modules) {
         if (module.isEnabled()) {
            module.onRender3D(camera, partialTick);
         }
      }
   }

   public void pollBinds() {
      boolean canFire = class_310.method_1551().field_1755 == null;

      for (Module module : this.modules) {
         Bind bind = module.keybind.getValue();
         boolean down = !bind.isEmpty() && bind.isDown();
         boolean wasDown = down ? !this.heldBinds.add(module) : this.heldBinds.remove(module);
         if (module.getBindMode() == Module.BindMode.HOLD) {
            if (down) {
               if (canFire && !module.isEnabled()) {
                  module.setEnabled(true);
               }
            } else if (module.isEnabled() && (wasDown || !bind.isEmpty())) {
               module.setEnabled(false);
            }
         } else if (down && !wasDown && canFire) {
            module.toggle();
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Module module : this.getModules()) {
         object.add(module.getName(), module.toJson());
      }

      return object;
   }

   public JsonObject toFunctionalJson() {
      JsonObject object = new JsonObject();
      object.addProperty("__partial", true);

      for (Module module : this.getModules()) {
         if (!module.isPersonal()) {
            object.add(module.getName(), module.toFunctionalJson());
         }
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      JsonObject object = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
      if (object != null && object.has("__partial")) {
         for (Module module : this.getModules()) {
            JsonElement stored = object.get(module.getName());
            if (stored != null && stored.isJsonObject()) {
               module.fromJson(stored);
            }
         }
      } else {
         JsonElement bundled = DefaultConfig.section(this.getFileName());
         JsonObject defaults = bundled != null && bundled.isJsonObject() ? bundled.getAsJsonObject() : null;

         for (Module module : this.getModules()) {
            JsonElement stored = object == null ? null : object.get(module.getName());
            if (stored != null) {
               module.fromJson(stored);
            } else {
               JsonElement fallback = defaults == null ? null : defaults.get(module.getName());
               if (fallback != null && fallback.isJsonObject()) {
                  JsonObject settings = fallback.getAsJsonObject().deepCopy();

                  for (Setting<?> setting : module.getSettings()) {
                     if (setting.getValue() instanceof Bind) {
                        settings.remove(setting.getName());
                     }
                  }

                  module.fromJson(settings);
               } else {
                  module.fromJson(null);
               }
            }
         }
      }
   }

   @Override
   public String getFileName() {
      return "modules.json";
   }
}
