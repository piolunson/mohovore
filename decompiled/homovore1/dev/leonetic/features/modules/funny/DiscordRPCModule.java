package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_640;

public class DiscordRPCModule extends Module {
   private static final long APP_ID = 1497806002139955270L;
   private final Setting<DiscordRPCModule.LineMode> line1Mode = this.mode("Line1Mode", DiscordRPCModule.LineMode.SERVER).setPage("General");
   private final Setting<String> line1Custom = this.str("Line1Custom", "Owning on [server]").setPage("General");
   private final Setting<DiscordRPCModule.LineMode> line2Mode = this.mode("Line2Mode", DiscordRPCModule.LineMode.STATS).setPage("General");
   private final Setting<String> line2Custom = this.str("Line2Custom", "HP: [health] | Ping: [ping]ms").setPage("General");
   private final Setting<Boolean> hideIp = this.bool("HideIP", false).setPage("General");
   private final Setting<Integer> antileak = this.num("AntiLeakRadius", 50000, 1000, 100000).setPage("General");
   private static final RichPresence rpc = new RichPresence();
   private int ticks;

   public DiscordRPCModule() {
      super("DiscordRPC", "Shows a highly customizable Discord Rich Presence.", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      DiscordIPC.start(1497806002139955270L, null);
      rpc.setStart(System.currentTimeMillis() / 1000L);
      rpc.setLargeImage("icon", "Homovore Client");
      this.ticks = 0;
   }

   @Override
   public void onDisable() {
      DiscordIPC.stop();
   }

   @Override
   public void onTick() {
      if (++this.ticks >= 20) {
         this.ticks = 0;
         if (!nullCheck()) {
            rpc.setDetails(this.formatLine(this.line1Mode.getValue(), this.line1Custom.getValue()));
            rpc.setState(this.formatLine(this.line2Mode.getValue(), this.line2Custom.getValue()));
            if (mc.field_1724 != null) {
               String name = mc.field_1724.method_5477().getString();
               rpc.setSmallImage("https://minotar.net/helm/" + name + "/100.png", name);
            }

            DiscordIPC.setActivity(rpc);
         }
      }
   }

   private String formatLine(DiscordRPCModule.LineMode mode, String customFormat) {
      return switch (mode) {
         case CUSTOM -> this.applyTokens(customFormat);
         case SERVER -> "Playing on " + this.getServerIp();
         case DIMENSION -> this.getDimensionText() + " " + this.getCoordsText();
         case HEALTH -> "Health: " + this.getHealth() + " | Ping: " + this.getPing() + "ms";
         case STATS -> "Totems: " + this.getTotems() + " | Modules: " + this.getActiveModules();
      };
   }

   private String applyTokens(String text) {
      return text == null
         ? ""
         : text.replace("[server]", this.getServerIp())
            .replace("[ping]", String.valueOf(this.getPing()))
            .replace("[health]", this.getHealth())
            .replace("[dimension]", this.getDimensionText())
            .replace("[coords]", this.getCoordsText())
            .replace("[totems]", String.valueOf(this.getTotems()))
            .replace("[modules]", String.valueOf(this.getActiveModules()));
   }

   private String getServerIp() {
      if (mc.method_1496()) {
         return "Singleplayer";
      } else if (mc.method_1558() != null) {
         return this.hideIp.getValue() ? "Private Server" : mc.method_1558().field_3761;
      } else {
         return "Main Menu";
      }
   }

   private String getDimensionText() {
      if (mc.field_1724 == null) {
         return "Unknown";
      }

      String dimPath = mc.field_1724.method_73183().method_27983().method_29177().method_12832();

      return switch (dimPath) {
         case "overworld" -> "Overworld";
         case "the_nether" -> "The Nether";
         case "the_end" -> "The End";
         default -> dimPath;
      };
   }

   private String getCoordsText() {
      if (mc.field_1724 == null) {
         return "";
      }

      int radius = this.antileak.getValue();
      return Math.abs(mc.field_1724.method_31477()) <= radius && Math.abs(mc.field_1724.method_31479()) <= radius
         ? String.format("[%d, %d, %d]", mc.field_1724.method_31477(), mc.field_1724.method_31478(), mc.field_1724.method_31479())
         : "[x, y, z]";
   }

   private String getHealth() {
      if (mc.field_1724 == null) {
         return "0.0";
      }

      float hp = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      return String.format("%.1f", hp);
   }

   private int getPing() {
      if (mc.field_1724 != null && mc.method_1562() != null) {
         class_640 info = mc.method_1562().method_2871(mc.field_1724.method_5667());
         return info == null ? 0 : info.method_2959();
      } else {
         return 0;
      }
   }

   private int getTotems() {
      if (mc.field_1724 == null) {
         return 0;
      }

      int count = 0;

      for (int i = 0; i < mc.field_1724.method_31548().method_5439(); i++) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(i);
         if (stack.method_7909() == class_1802.field_8288) {
            count += stack.method_7947();
         }
      }

      return count;
   }

   private int getActiveModules() {
      return (int)Homovore.moduleManager.getModules().stream().filter(Module::isEnabled).count();
   }

   public enum LineMode {
      CUSTOM,
      SERVER,
      DIMENSION,
      HEALTH,
      STATS;
   }
}
